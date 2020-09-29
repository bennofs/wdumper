package io.github.bennofs.wdumper.web;

import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.model.Run;
import io.github.bennofs.wdumper.model.Progress;
import io.github.bennofs.wdumper.model.RunStats;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * This class is responsible for estimating the progress of a run
 * (what percentage is already done and how long it will take until the run is finished)
 */
public class ProgressEstimator {
    private RunStats statsCache;
    private Instant lastUpdate;
    private final Database db;

    private final Duration MAX_STALE = Duration.ofHours(2);

    @Inject
    public ProgressEstimator(Database db) {
        this.db = db;
    }

    private RunStats getStats() {
        if (lastUpdate == null || Duration.between(lastUpdate, Instant.now()).compareTo(MAX_STALE) > 0) {
            lastUpdate = Instant.now();
            statsCache = db.getRecentRunStats();
        }

        return statsCache;
    }

    public Optional<Progress> estimate(Run r) {
        if (r == null || r.startedAt().isEmpty()) return Optional.empty();

        final Instant startedAt = r.startedAt().get();

        final RunStats stats = getStats();
        final long percentCompleted = r.count() * 100 / stats.entityCount;
        final Duration estimatedRemaining = stats.averageTimePerEntity.multipliedBy(stats.entityCount - r.count());

        return Optional.of(new Progress(startedAt, estimatedRemaining, percentCompleted));
    }
}
