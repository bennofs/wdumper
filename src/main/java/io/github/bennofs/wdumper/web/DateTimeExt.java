package io.github.bennofs.wdumper.web;

import io.github.bennofs.wdumper.formatting.TimeFormatting;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

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
