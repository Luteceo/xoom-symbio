// Copyright © 2012-2022 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.symbio.store.journal;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.actors.DeadLetter;
import io.vlingo.xoom.actors.LocalMessage;
import io.vlingo.xoom.actors.Mailbox;
import io.vlingo.xoom.common.Outcome;
import io.vlingo.xoom.common.SerializableConsumer;
import io.vlingo.xoom.symbio.Metadata;
import io.vlingo.xoom.symbio.Source;
import io.vlingo.xoom.symbio.store.Result;
import io.vlingo.xoom.symbio.store.StorageException;
import io.vlingo.xoom.symbio.store.journal.Journal.AppendResultInterest;

import java.util.List;
import java.util.Optional;

public class JournalAppendResultInterest__Proxy implements io.vlingo.xoom.symbio.store.journal.Journal.AppendResultInterest {

  private static final String appendResultedInRepresentation1 = "appendResultedIn(io.vlingo.xoom.common.Outcome<io.vlingo.xoom.symbio.store.StorageException, io.vlingo.xoom.symbio.store.Result>, java.lang.String, int, io.vlingo.xoom.symbio.Source<S>, java.util.Optional<ST>, java.lang.Object)";
  private static final String appendResultedInRepresentation2 = "appendResultedIn(io.vlingo.xoom.common.Outcome<io.vlingo.xoom.symbio.store.StorageException, io.vlingo.xoom.symbio.store.Result>, java.lang.String, int, io.vlingo.xoom.symbio.Source<S>, io.vlingo.xoom.symbio.Metadata,  java.util.Optional<ST>, java.lang.Object)";
  private static final String appendAllResultedInRepresentation1 = "appendAllResultedIn(io.vlingo.xoom.common.Outcome<io.vlingo.xoom.symbio.store.StorageException, io.vlingo.xoom.symbio.store.Result>, java.lang.String, int, java.util.List<io.vlingo.xoom.symbio.Source<S>>, java.util.Optional<ST>, java.lang.Object)";
  private static final String appendAllResultedInRepresentation2 = "appendAllResultedIn(io.vlingo.xoom.common.Outcome<io.vlingo.xoom.symbio.store.StorageException, io.vlingo.xoom.symbio.store.Result>, java.lang.String, int, java.util.List<io.vlingo.xoom.symbio.Source<S>>, io.vlingo.xoom.symbio.Metadata, java.util.Optional<ST>, java.lang.Object)";

  private final Actor actor;
  private final Mailbox mailbox;

  public JournalAppendResultInterest__Proxy(final Actor actor, final Mailbox mailbox){
    this.actor = actor;
    this.mailbox = mailbox;
  }

  public <S,ST>void appendResultedIn(final io.vlingo.xoom.common.Outcome<io.vlingo.xoom.symbio.store.StorageException, io.vlingo.xoom.symbio.store.Result> arg0, final java.lang.String arg1, final int arg2, io.vlingo.xoom.symbio.Source<S> arg3, final java.util.Optional<ST> arg4, final java.lang.Object arg5) {
    final SerializableConsumer<AppendResultInterest> consumer = (actor) -> actor.appendResultedIn(arg0, arg1, arg2, arg3, arg4, arg5);
    send(JournalAppendResultInterest__Proxy.appendResultedInRepresentation1, consumer);
  }

  @Override
  public <S, ST> void appendResultedIn(final Outcome<StorageException, Result> outcome, final String streamName, final int streamVersion,
          Source<S> source, Metadata metadata, Optional<ST> snapshot, Object object) {
    final SerializableConsumer<AppendResultInterest> consumer = (actor) -> actor.appendResultedIn(outcome, streamName, streamVersion, source, metadata, snapshot, object);
    send(JournalAppendResultInterest__Proxy.appendResultedInRepresentation2, consumer);
  }

  public <S,ST>void appendAllResultedIn(final io.vlingo.xoom.common.Outcome<io.vlingo.xoom.symbio.store.StorageException, io.vlingo.xoom.symbio.store.Result> arg0, final java.lang.String arg1, final int arg2, java.util.List<io.vlingo.xoom.symbio.Source<S>> arg3, final java.util.Optional<ST> arg4, final java.lang.Object arg5) {
    final SerializableConsumer<AppendResultInterest> consumer = (actor) -> actor.appendAllResultedIn(arg0, arg1, arg2, arg3, arg4, arg5);
    send(JournalAppendResultInterest__Proxy.appendAllResultedInRepresentation1, consumer);
  }

  @Override
  public <S, ST> void appendAllResultedIn(final Outcome<StorageException, Result> outcome, final String streamName,
          final int streamVersion, final List<Source<S>> sources, final Metadata metadata, Optional<ST> snapshot, final Object object) {
    final SerializableConsumer<AppendResultInterest> consumer = (actor) -> actor.appendAllResultedIn(outcome, streamName, streamVersion, sources, metadata, snapshot, object);
    send(JournalAppendResultInterest__Proxy.appendAllResultedInRepresentation2, consumer);
  }

  private void send(final String representation, final SerializableConsumer<AppendResultInterest> consumer) {
    if (!actor.isStopped()) {
      if (mailbox.isPreallocated()) {
        mailbox.send(actor, AppendResultInterest.class, consumer, null, representation);
      } else {
        mailbox.send(new LocalMessage<>(actor, AppendResultInterest.class, consumer, representation));
      }
    } else {
      actor.deadLetters().failedDelivery(new DeadLetter(actor, representation));
    }
  }

}
