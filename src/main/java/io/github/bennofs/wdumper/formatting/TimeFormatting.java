package io.github.bennofs.wdumper.formatting;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public class TimeFormatting {
    public String humanTimeBetween(Instant from, Instant to) {
        final ZonedDateTime toUtc = to.atZone(ZoneOffset.UTC);
        final ZonedDateTime fromUtc = from.atZone(ZoneOffset.UTC);

        final Duration d = Duration.between(from, to);
        final long years = ChronoUnit.YEARS.between(fromUtc, toUtc);
        final long months = ChronoUnit.MONTHS.between(fromUtc, toUtc);
        final long days = d.toDaysPart();
        final long hours = d.toHoursPart();
        final long minutes = d.toMinutesPart();
        final long seconds = d.toSecondsPart();

        if (years > 0) {
            if (months == 0) {
                return String.format("%dy", years);
            }

            return String.format("%dy:%ddmo", years, months);
        }

        if (months > 0) {
            if (days == 0) {
                return String.format("%dmo", months);
            }

            return String.format("%dmo:%dd", months, days);
        }

        if (days > 0) {
            if (hours == 0) {
                return String.format("%dd", days);
            }

            return String.format("%dd:%dh", days, hours);
        }

        if (hours > 0) {
            if (minutes == 0) {
                return String.format("%dh", hours);
            }

            return String.format("%dh:%dmin", hours, minutes);
        }

        if (minutes > 0) {
            if (seconds == 0) {
                return String.format("%dmin", minutes);
            }

            return String.format("%dmin:%ds", minutes, seconds);
        }

        return String.format("%ds", seconds);
    }
}
