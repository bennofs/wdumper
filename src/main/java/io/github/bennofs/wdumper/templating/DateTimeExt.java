package io.github.bennofs.wdumper.templating;

import io.github.bennofs.wdumper.templating.TimeFormatting;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class DateTimeExt {
    private final Instant time;
    private final TimeFormatting formatting;

    public DateTimeExt(Instant time, TimeFormatting formatting) {
        this.time = time;
        this.formatting = formatting;
    }

    public String instant() {
        return DateTimeFormatter.ISO_INSTANT.format(time);
    }

    public String since() {
        return formatting.humanTimeBetween(time, Instant.now());
    }
}
