// Copyright © 2012-2018 Vaughn Vernon. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.symbio.store.object.inmemory;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.vlingo.actors.Actor;
import io.vlingo.actors.Definition;
import io.vlingo.common.Completes;
import io.vlingo.common.Failure;
import io.vlingo.common.Success;
import io.vlingo.symbio.BaseEntry;
import io.vlingo.symbio.Entry;
import io.vlingo.symbio.EntryAdapterProvider;
import io.vlingo.symbio.Metadata;
import io.vlingo.symbio.Source;
import io.vlingo.symbio.State;
import io.vlingo.symbio.StateAdapterProvider;
import io.vlingo.symbio.store.EntryReader;
import io.vlingo.symbio.store.Result;
import io.vlingo.symbio.store.StorageException;
import io.vlingo.symbio.store.dispatch.Dispatchable;
import io.vlingo.symbio.store.dispatch.Dispatcher;
import io.vlingo.symbio.store.dispatch.DispatcherControl;
import io.vlingo.symbio.store.dispatch.control.DispatcherControlActor;
import io.vlingo.symbio.store.object.ObjectStore;
import io.vlingo.symbio.store.object.ObjectStoreDelegate;
import io.vlingo.symbio.store.object.ObjectStoreEntryReader;
import io.vlingo.symbio.store.object.PersistentObject;
import io.vlingo.symbio.store.object.QueryExpression;
import io.vlingo.symbio.store.state.StateStoreEntryReader;

/**
 * In-memory implementation of {@code ObjectStore}. Note that {@code queryAll()} variations
 * do not support select constraints but always select all stored objects.
 */
public class InMemoryObjectStoreActor extends Actor implements ObjectStore {
  private final EntryAdapterProvider entryAdapterProvider;

  private final Dispatcher<Dispatchable<BaseEntry<?>,State<?>>> dispatcher;
  private final DispatcherControl dispatcherControl;
  private final Map<String,StateStoreEntryReader<?>> entryReaders;

  private final ObjectStoreDelegate<BaseEntry<?>,State<?>> storeDelegate;

  /**
   * Construct my default state.
   * @param dispatcher The dispatcher to be used
   */
  public InMemoryObjectStoreActor(final Dispatcher<Dispatchable<BaseEntry<?>,State<?>>> dispatcher){
    this(dispatcher, 1000L, 1000L);
  }

  public InMemoryObjectStoreActor(final Dispatcher<Dispatchable<BaseEntry<?>,State<?>>> dispatcher,
         final long checkConfirmationExpirationInterval, final long confirmationExpiration ) {
    this.entryAdapterProvider = EntryAdapterProvider.instance(stage().world());
    this.dispatcher = dispatcher;

    this.entryReaders = new HashMap<>();

    this.storeDelegate = new InMemoryObjectStoreDelegate(StateAdapterProvider.instance(stage().world()));

    this.dispatcherControl = stage().actorFor(
            DispatcherControl.class,
            Definition.has(
                    DispatcherControlActor.class,
                    Definition.parameters(
                            dispatcher,
                            this.storeDelegate,
                            checkConfirmationExpirationInterval,
                            confirmationExpiration)));
  }

  /*
   * @see io.vlingo.symbio.store.object.ObjectStore#close()
   */
  @Override
  public void close() {
    this.storeDelegate.close();
  }

  /*
   * @see io.vlingo.symbio.store.object.ObjectStoreReader#entryReader(java.lang.String)
   */
  @Override
  @SuppressWarnings("unchecked")
  public Completes<EntryReader<? extends Entry<?>>> entryReader(final String name) {
    EntryReader<? extends Entry<?>> reader = entryReaders.get(name);
    if (reader == null) {
      final Definition definition = Definition.has(InMemoryObjectStoreEntryReaderActor.class, Definition.parameters(readOnlyJournal(), name));
      reader = childActorFor(ObjectStoreEntryReader.class, definition);
    }
    return completes().with(reader);
  }

  /* @see io.vlingo.symbio.store.object.ObjectStore#persist(java.lang.Object, java.util.List, long, io.vlingo.symbio.store.object.ObjectStore.PersistResultInterest, java.lang.Object) */
  @Override
  public <T extends PersistentObject, E> void persist(
          final T persistentObject, final List<Source<E>> sources, final Metadata metadata,
          final long updateId, final PersistResultInterest interest, final Object object) {
    try {
      final State<?> raw = storeDelegate.persist(persistentObject, updateId, metadata);

      final List<BaseEntry<?>> entries = entryAdapterProvider.asEntries(sources, metadata);
      final Dispatchable<BaseEntry<?>, State<?>> dispatchable = buildDispatchable(raw, entries);

      this.storeDelegate.persistEntries(entries);
      this.storeDelegate.persistDispatchable(dispatchable);

      dispatch(dispatchable);
      interest.persistResultedIn(Success.of(Result.Success), persistentObject, 1, 1, object);
    } catch (StorageException e){
      logger().error("Failed to persist all objects", e);
      interest.persistResultedIn(Failure.of(e), null, 0, 0, object);
    }
  }


  /* @see io.vlingo.symbio.store.object.ObjectStore#persistAll(java.util.Collection, java.util.List, long, io.vlingo.symbio.store.object.ObjectStore.PersistResultInterest, java.lang.Object) */
  @Override
  public <T extends PersistentObject, E> void persistAll(final Collection<T> persistentObjects, final List<Source<E>> sources,
          final Metadata metadata, final long updateId, final PersistResultInterest interest, final Object object) {

    try {
      final Collection<State<?>> states = storeDelegate.persistAll(persistentObjects, updateId, metadata);

      final List<BaseEntry<?>> entries = entryAdapterProvider.asEntries(sources, metadata);

      this.storeDelegate.persistEntries(entries);

      states.forEach(state -> {
        final Dispatchable<BaseEntry<?>, State<?>> dispatchable = buildDispatchable(state, entries);
        this.storeDelegate.persistDispatchable(dispatchable);
        //dispatch each persistent object
        dispatch(buildDispatchable(state, entries));
      });

      interest.persistResultedIn(Success.of(Result.Success), persistentObjects, persistentObjects.size(), persistentObjects.size(), object);
    } catch (final StorageException e){
      logger().error("Failed to persist all objects", e);
      interest.persistResultedIn(Failure.of(e), null, 0, 0, object);
    }
  }

  /*
   * @see io.vlingo.symbio.store.object.ObjectStore#queryAll(io.vlingo.symbio.store.object.QueryExpression, io.vlingo.symbio.store.object.ObjectStore.QueryResultInterest, java.lang.Object)
   */
  @Override
  public void queryAll(final QueryExpression expression, final QueryResultInterest interest, final Object object) {
    final QueryMultiResults queryMultiResults = this.storeDelegate.queryAll(expression);
    interest.queryAllResultedIn(Success.of(Result.Success), queryMultiResults, object);
  }

  /*
   * @see io.vlingo.symbio.store.object.ObjectStore#queryObject(io.vlingo.symbio.store.object.QueryExpression, io.vlingo.symbio.store.object.ObjectStore.QueryResultInterest, java.lang.Object)
   */
  @Override
  public void queryObject(final QueryExpression expression, final QueryResultInterest interest, final Object object) {
    final QuerySingleResult result = this.storeDelegate.queryObject(expression);

    if (result.persistentObject != null) {
      interest.queryObjectResultedIn(Success.of(Result.Success), result, object);
    } else {
      interest.queryObjectResultedIn(Failure.of(new StorageException(Result.NotFound, "No object identified by expression: " + expression)), QuerySingleResult.of(null), object);
    }
  }

  @Override
  public void stop() {
    dispatcherControl.stop();
    super.stop();
  }


  private void dispatch(final Dispatchable<BaseEntry<?>, State<?>> dispatchable){
    this.dispatcher.dispatch(dispatchable);
  }

  private static Dispatchable<BaseEntry<?>, State<?>> buildDispatchable(final State<?> state, final List<BaseEntry<?>> entries) {
    final String id = getDispatchId(state, entries);
    return new Dispatchable<>(id, LocalDateTime.now(), state, entries);
  }

  private static String getDispatchId(final State<?> raw, final List<BaseEntry<?>> entries) {
    return raw.id + ":" + entries.stream().map(Entry::id).collect(Collectors.joining(":"));
  }

  private List<BaseEntry<?>> readOnlyJournal() {
    return ((InMemoryObjectStoreDelegate) storeDelegate).readOnlyJournal();
  }
}
