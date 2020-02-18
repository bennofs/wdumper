package io.github.bennofs.wdumper.zenodo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;

import javax.annotation.Nullable;
import java.util.Collection;

@AutoValue
@JsonDeserialize(builder = AutoValue_Metadata.Builder.class)
public abstract class Metadata {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PrereserveDOI {
        public String doi;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("doi", doi)
                    .toString();
        }
    }

    @JsonProperty
    public abstract @Nullable String title();

    @JsonProperty
    public abstract @Nullable String description();

    @JsonProperty
    public abstract @Nullable String uploadType();

    @JsonProperty
    public abstract @Nullable String accessRight();

    @JsonProperty
    public abstract @Nullable String license();

    @JsonProperty
    public abstract PrereserveDOI prereserveDoi();

    @JsonProperty
    public abstract @Nullable Collection<Creator> creators();

    public abstract @Nullable String doi();

    public abstract Builder toBuilder();

    static Builder builder() {
        return new AutoValue_Metadata.Builder();
    }

    @AutoValue.Builder
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public abstract static class Builder {
        public abstract Builder title(String title);

        public abstract Builder description(String description);

        public abstract Builder uploadType(String uploadType);

        public abstract Builder accessRight(String accessRight);

        public abstract Builder license(String license);

        public abstract Builder creators(Collection<Creator> creators);

        public abstract Builder prereserveDoi(PrereserveDOI doi);

        abstract Builder doi(String doi);

        public abstract Metadata build();
    }
}
