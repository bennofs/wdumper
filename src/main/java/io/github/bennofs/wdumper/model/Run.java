package io.github.bennofs.wdumper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@AutoValue
public abstract class Run {
    @JsonProperty
    public abstract Integer id();

    @JsonProperty
    public abstract Optional<Instant> startedAt();

    @JsonProperty
    public abstract Optional<Instant> finishedAt();

    @JsonProperty
    public abstract Integer count();

    @JsonProperty
    public abstract String toolVersion();

    @JsonProperty
    public abstract String wdtkVersion();

    @JsonProperty
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
