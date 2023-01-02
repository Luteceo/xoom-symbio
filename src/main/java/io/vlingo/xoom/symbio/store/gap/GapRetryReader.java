// Copyright © 2012-2023 VLINGO LABS. All rights reserved.
//
// This Source Code Form is subject to the terms of the
// Mozilla Public License, v. 2.0. If a copy of the MPL
// was not distributed with this file, You can obtain
// one at https://mozilla.org/MPL/2.0/.

package io.vlingo.xoom.symbio.store.gap;

import io.vlingo.xoom.actors.Actor;
import io.vlingo.xoom.actors.CompletesEventually;
import io.vlingo.xoom.actors.Stage;
import io.vlingo.xoom.common.Scheduled;
import io.vlingo.xoom.common.Scheduler;
import io.vlingo.xoom.symbio.Entry;
import io.vlingo.xoom.symbio.store.EntryReader;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Detection and fill up (gap prevention) functionality related to {@link EntryReader}.
 *
 * @param <T> Subtype of {@link Entry}
 */
public class GapRetryReader<T extends Entry<?>> {
    private final Scheduled<RetryGappedEntries<T>> actor;
    private final Scheduler scheduler;

    @SuppressWarnings("unchecked")
    public GapRetryReader(Stage stage, Scheduler scheduler) {
        this.actor = stage.actorFor(Scheduled.class, GapsFillUpActor.class);
        this.scheduler = scheduler;
    }

    private Set<Long> collectIds(List<T> entries) {
        if (entries == null) {
            return new HashSet<>();
        } else {
            return entries.stream()
                    .map(e -> Long.parseLong(e.id()))
                    .collect(Collectors.toSet());
        }
    }

    /**
     * Single entry variant method of {@link #detectGaps(List, long, long)}.
     * If the entry is loaded successfully, no gap is detected. Otherwise a gap with one id is detected.
     *
     * @param entry The entry to detect gap for
     * @param startIndex This index refers to {@link Entry#id()}
     * @return One element list if entry is not loaded.
     */
    public List<Long> detectGaps(T entry, long startIndex) {
        List<T> entries = entry == null ? new ArrayList<>() : Collections.singletonList(entry);
        return detectGaps(entries, startIndex, 1);
    }

    /**
     * Detect gaps in entries.
     *
     * @param entries Entries to detect gaps for
     * @param startIndex Start index to check for. This index refers to {@link Entry#id()}
     * @param count How many elements the list has to contain if no gaps would be present
     * @return Empty list if no gaps have been detected
     */
    public List<Long> detectGaps(List<T> entries, long startIndex, long count) {
        Set<Long> allIds = collectIds(entries);
        List<Long> gapIds = new ArrayList<>();

        for (long index = 0; index < count; index++) {
            if (!allIds.contains(startIndex + index)) {
                gapIds.add(startIndex + index);
            }
        }

        return gapIds;
    }

    /**
     * Retry reading missing gaps.
     *
     * @param gappedEntries Successfully already loaded entries
     * @param retries How many times to retry filling up gaps
     * @param retryInterval Interval between retries
     * @param gappedReader Function which reads the the specified gaps based on ids.
     */
    public void readGaps(GappedEntries<T> gappedEntries, int retries, long retryInterval, Function<List<Long>, List<T>> gappedReader) {
        RetryGappedEntries<T> entries = new RetryGappedEntries<>(gappedEntries, 1, retries, retryInterval, gappedReader);
        scheduler.scheduleOnce(actor, entries, 0L, retryInterval);
    }

    static class RetryGappedEntries<T extends Entry<?>> {
        private final GappedEntries<T> gappedEntries;
        private final int currentRetry;
        private final int retries;
        private final long retryInterval;
        private final Function<List<Long>, List<T>> gappedReader;

        RetryGappedEntries(GappedEntries<T> gappedEntries, int currentRetry, int retries, long retryInterval, Function<List<Long>, List<T>> gappedReader) {
            this.gappedEntries = gappedEntries;
            this.currentRetry = currentRetry;
            this.retries = retries;
            this.retryInterval = retryInterval;
            this.gappedReader = gappedReader;
        }

        boolean moreRetries() {
            return currentRetry < retries;
        }

        RetryGappedEntries<T> nextRetry(GappedEntries<T> nextGappedEntries) {
            return new RetryGappedEntries<>(nextGappedEntries, currentRetry + 1, retries, retryInterval, gappedReader);
        }
    }

    public static class GapsFillUpActor<T extends Entry<?>> extends Actor implements Scheduled<RetryGappedEntries<T>> {
        @Override
        public void intervalSignal(Scheduled<RetryGappedEntries<T>> scheduled, RetryGappedEntries<T> data) {
            Function<List<Long>, List<T>> gappedReader = data.gappedReader;
            List<T> fillups = gappedReader.apply(data.gappedEntries.getGapIds());
            GappedEntries<T> nextGappedEntries = data.gappedEntries.fillupWith(fillups);

            if (!nextGappedEntries.containGaps() || !data.moreRetries()) {
                CompletesEventually eventually = data.gappedEntries.getEventually();
                if (nextGappedEntries.size() == 1) {
                    // Only one entry has to be returned.
                    // {@link EntryReader<T>} - read one Entry<T> method.
                    eventually.with(nextGappedEntries.getFirst().orElse(null));
                } else {
                    // {@link EntryReader<T>} - read a list of Entry<T>
                    eventually.with(nextGappedEntries.getSortedLoadedEntries());
                }
            } else {
                RetryGappedEntries<T> nextData = data.nextRetry(nextGappedEntries);
                scheduler().scheduleOnce(scheduled, nextData, 0L, data.retryInterval);
            }
        }
    }
}