package io.github.bennofs.wdumper.model;

import java.time.Duration;

public class RunStats {
    public final Duration averageTimePerEntity;
    public final long entityCount;

    public RunStats(Duration averageTimePerEntity, long entityCount) {
        this.averageTimePerEntity = averageTimePerEntity;
        this.entityCount = entityCount;
    }
}
