// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.symbio.store.object.inmemory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.vlingo.xoom.actors.testkit.AccessSafely;
import io.vlingo.xoom.common.Outcome;
import io.vlingo.xoom.symbio.store.Result;
import io.vlingo.xoom.symbio.store.StorageException;
import io.vlingo.xoom.symbio.store.object.ObjectStoreReader.QueryMultiResults;
import io.vlingo.xoom.symbio.store.object.ObjectStoreReader.QueryResultInterest;
import io.vlingo.xoom.symbio.store.object.ObjectStoreReader.QuerySingleResult;

public class MockQueryResultInterest implements QueryResultInterest {
  private AccessSafely access = AccessSafely.afterCompleting(1);
  private final List<Object> stateObjects = new ArrayList<>();

  @Override
  public void queryAllResultedIn(
          final Outcome<StorageException, Result> outcome,
          final QueryMultiResults results,
          final Object object) {

    access.writeUsing("addAll", results.stateObjects);
  }

  @Override
  public void queryObjectResultedIn(
          final Outcome<StorageException, Result> outcome,
          final QuerySingleResult result,
          final Object object) {

    outcome
      .andThen(good -> good)
      .otherwise(bad -> { throw new IllegalStateException("Bogus outcome: " + bad.getMessage()); });

    access.writeUsing("add", result.stateObject);
  }

  @SuppressWarnings("unchecked")
  public AccessSafely afterCompleting(final int times) {
    access =
            AccessSafely
              .afterCompleting(times)
              .writingWith("add", (value) -> stateObjects.add(value))
              .writingWith("addAll", (values) -> stateObjects.addAll((Collection<Object>) values))
              .readingWith("object", (index) -> stateObjects.get((int) index))
              .readingWith("size", () -> stateObjects.size());

    return access;
  }
}
