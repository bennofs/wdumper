package io.github.bennofs.wdumper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

@AutoValue
public abstract class Dump {
    @JsonProperty
    public abstract int id();

    @JsonProperty
    public abstract String title();

    @JsonProperty
    public abstract String description();

    @JsonProperty
    public abstract String spec();

    @JsonProperty
    public abstract Instant createdAt();

    @JsonProperty
    public abstract Optional<Integer> runId();

    @JsonProperty
    public abstract Long compressedSize();

    @JsonProperty
    public abstract Long entityCount();

    @JsonProperty
    public abstract Long statementCount();

    @JsonProperty
    public abstract Long tripleCount();

    public abstract Builder toBuilder();
    public static Builder builder() {
        return new AutoValue_Dump.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(int id);

        public abstract Builder title(String title);

        public abstract Builder description(String description);

        public abstract Builder spec(String spec);

        public abstract Builder createdAt(Instant createdAt);

        public abstract Builder runId(@Nullable Integer runId);

        public abstract Builder compressedSize(Long compressedSize);

        public abstract Builder entityCount(Long entityCount);

        public abstract Builder statementCount(Long statementCount);

        public abstract Builder tripleCount(Long tripleCount);

        public abstract Dump build();
    }
}
