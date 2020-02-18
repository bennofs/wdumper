package io.github.bennofs.wdumper.zenodo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;

@AutoValue
@JsonDeserialize(builder = AutoValue_Creator.Builder.class)
public abstract class Creator {
    @JsonProperty
    public abstract String name();

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public abstract @Nullable
    String affiliation();

    public static Builder builder() {
        return new AutoValue_Creator.Builder();
    }

    @AutoValue.Builder
    @JsonPOJOBuilder(withPrefix = "")
    public static abstract class Builder {
        public abstract Builder name(String name);
        public abstract Builder affiliation(String affiliation);
        public abstract Creator build();
    }
}
