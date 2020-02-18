package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/**
 * Part of the specification that controls how and which statements are to be exported for a single wikidata entity.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_StatementFilterJson.Builder.class)
public abstract class StatementFilterJson {
    /**
     * Only apply this filter to statements for these properties.
     *
     * If null, apply to all statements, regardless of their property.
     */
    @JsonProperty
    public abstract @Nullable ImmutableSet<String> properties();

    /**
     * Only include statements if they match the given rank filter.
     */
    @JsonProperty
    public abstract RankFilter rank();

    /**
     * If true, include the simple representation for this statement.
     *
     * Note that to avoid confusion, the simple form of a statement is only generated for best-rank statements.
     */
    @JsonProperty
    public abstract boolean simple();

    /**
     * If true, include the full node for this statement.
     */
    @JsonProperty
    public abstract boolean full();

    /**
     * If true, include the references for this statement.
     */
    @JsonProperty
    public abstract boolean references();

    /**
     * If true, include qualifiers for this statement.
     */
    @JsonProperty
    public abstract boolean qualifiers();

    public static Builder builder() {
        return new AutoValue_StatementFilterJson.Builder();
    }

    @AutoValue.Builder
    @JsonPOJOBuilder(withPrefix = "", buildMethodName = "jsonBuild")
    @JsonIgnoreProperties({"id"})
    public abstract static class Builder {
        public abstract Builder properties(Set<String> properties);

        public abstract Builder rank(RankFilter rank);

        public abstract Builder simple(boolean simple);

        public abstract Builder full(boolean full);

        public abstract Builder references(boolean references);

        public abstract Builder qualifiers(boolean qualifiers);

        public abstract StatementFilterJson build();

        abstract Optional<RankFilter> rank();

        @SuppressWarnings({"unused"})
        StatementFilterJson jsonBuild() {
            if (rank().isEmpty()) rank(RankFilter.ALL);

            return build();
        }
    }
}
