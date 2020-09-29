package io.github.bennofs.wdumper.model;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@AutoValue
public abstract class Run {
    public abstract Integer id();
    public abstract Optional<Instant> startedAt();
    public abstract Optional<Instant> finishedAt();
    public abstract Integer count();
    public abstract String toolVersion();
    public abstract String wdtkVersion();
    public abstract String dumpDate();

    public abstract Builder toBuilder();
    public static Builder builder() {
        return new AutoValue_Run.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(Integer id);

        public abstract Builder startedAt(@Nullable Instant startedAt);

        public abstract Builder finishedAt(@Nullable Instant finishedAt);

        public abstract Builder count(Integer count);

        public abstract Builder toolVersion(String toolVersion);

        public abstract Builder wdtkVersion(String wdtkVersion);

        public abstract Builder dumpDate(String dumpDate);

        public abstract Run build();
    }
}
