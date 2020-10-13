package io.github.bennofs.wdumper.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Optional;

@AutoValue
public abstract class Zenodo {
    public enum Target {
        @JsonProperty("sandbox")
        SANDBOX,

        @JsonProperty("release")
        RELEASE
    }

    private static final String ZENODO_SANDBOX_URL_PREFIX = "https://sandbox.zenodo.org/record/";
    private static final String DOI_URL_PREFIX = "https://doi.org/";

    @JsonProperty
    public abstract int id();

    @JsonProperty
    public abstract int depositId();

    @JsonProperty
    public abstract int dumpId();

    @JsonProperty
    public abstract String doi();

    @JsonProperty
    public abstract Target target();

    @JsonProperty
    public abstract Instant createdAt();

    @JsonProperty
    public abstract Optional<Instant> startedAt();

    @JsonProperty
    public abstract Optional<Instant> completedAt();

    @JsonProperty
    public abstract long uploadedBytes();

    public String link() {
        switch (target()) {
            case SANDBOX:
                return ZENODO_SANDBOX_URL_PREFIX + depositId();
            case RELEASE:
                return DOI_URL_PREFIX + doi();
            default:
                throw new IllegalStateException("zenodo target neither SANDBOX nor RELEASE: " + target());
        }
    }

    public abstract Builder toBuilder();
    public static Builder builder() {
        return new AutoValue_Zenodo.Builder();
    }


    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder id(int id);

        public abstract Builder depositId(int depositId);

        public abstract Builder dumpId(int dumpId);

        public abstract Builder doi(String doi);

        public abstract Builder target(Target target);

        public abstract Builder createdAt(Instant createdAt);

        public abstract Builder startedAt(@Nullable Instant startedAt);

        public abstract Builder completedAt(@Nullable Instant completedAt);

        public abstract Builder uploadedBytes(long uploadedBytes);

        public abstract Zenodo build();
    }
}
