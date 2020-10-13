package io.github.bennofs.wdumper.templating;

import io.github.bennofs.wdumper.templating.TimeFormatting;

import java.time.Duration;
import java.time.Instant;

public class DurationExt {
    private final TimeFormatting formatting;
    private final Duration duration;

    public DurationExt(Duration duration, TimeFormatting formatting) {
        this.formatting = formatting;
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }

    public String until() {
        final Instant now = Instant.now();
        return formatting.humanTimeBetween(now, now.plus(duration));
    }
}
