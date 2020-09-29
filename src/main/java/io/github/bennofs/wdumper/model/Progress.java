package io.github.bennofs.wdumper.model;

import java.time.Duration;
import java.time.Instant;

public class Progress {
    public final Instant startedAt;
    public final Duration estimatedRemaining;
    public final long completedPercent;

    public Progress(Instant startedAt, Duration estimatedRemaining, long completedPercent) {
        this.startedAt = startedAt;
        this.estimatedRemaining = estimatedRemaining;
        this.completedPercent = completedPercent;
    }
}
