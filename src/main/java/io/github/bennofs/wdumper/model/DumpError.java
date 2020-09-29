package io.github.bennofs.wdumper.model;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

@AutoValue
public abstract class DumpError {
    public enum Level {
        CRITICAL,
        ERROR,
        WARNING;

        public String levelName() {
            switch (this) {
                case CRITICAL:
                    return "critical";
                case ERROR:
                    return "error";
                case WARNING:
                    return "warning";
                default:
                    throw new IllegalArgumentException("invalid error level: " + this);
            }
        }
    }

    public abstract int id();
    public abstract Instant loggedAt();
    public abstract Optional<Integer> dumpId();
    public abstract Optional<Integer> runId();
    public abstract Optional<Integer> zenodoId();
    public abstract Level level();
    public abstract String message();

    public abstract Builder toBuilder();
    public static Builder builder() {
        return new AutoValue_DumpError.Builder();
    }

    public String category() {
        if (this.zenodoId().isPresent()) return "ZENODO";
        if (this.dumpId().isPresent()) return "DUMP";
        if (this.runId().isPresent()) return "RUN";
        return "UNKNOWN";
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(int id);

        public abstract Builder loggedAt(Instant loggedAt);

        public abstract Builder dumpId(Integer dumpId);
        public abstract Builder dumpId(Optional<Integer> dumpId);

        public abstract Builder runId(Integer runId);
        public abstract Builder runId(Optional<Integer> runId);

        public abstract Builder zenodoId(Integer zenodoId);
        public abstract Builder zenodoId(Optional<Integer> zenodoId);

        public abstract Builder level(Level level);

        public abstract Builder message(String message);

        public abstract DumpError build();
    }
}
