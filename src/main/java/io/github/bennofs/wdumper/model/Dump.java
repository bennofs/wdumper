package io.github.bennofs.wdumper.model;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@AutoValue
public abstract class Dump {
    public abstract int id();
    public abstract String title();
    public abstract String description();
    public abstract String spec();
    public abstract Instant createdAt();
    public abstract Optional<Integer> runId();
    public abstract Long compressedSize();
    public abstract Long entityCount();
    public abstract Long statementCount();
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
