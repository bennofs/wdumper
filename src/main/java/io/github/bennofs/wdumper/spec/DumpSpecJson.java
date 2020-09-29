package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.Validate;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A specification for a dump contains all options and filters that control the dump generation.
 *
 * This specification should be complete, i.e. given the same dump input, the specification should contain
 * enough information to reproduce a filtered dump exactly.
 *
 * The specification has a JSON representation that is used for communication with the frontend.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_DumpSpecJson.Builder.class)
public abstract class DumpSpecJson {
    /**
     * @return The version of this dump spec.
     */
    @JsonProperty
    public abstract DumpSpecVersion version();

    /**
     * @return Filters to restrict the entities that are included in the dump.
     */
    @JsonProperty
    public abstract List<EntityFilterJson> entities();

    /**
     * A sampling rate to reduce the number of entities included in the dump. This is applied together with the
     * entity filter, so an entity is only included if it matches all the filters.
     *
     * @return The sampling rate in percent
     */
    @JsonProperty
    public abstract int samplingPercent();

    /**
     * @return The seed for the random number generate used for sampling.
     */
    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_ABSENT)
    public abstract OptionalLong seed();

    /**
     * @return Filters to apply to statements of the entities which are included in the dump.
     */
    @JsonProperty
    public abstract List<StatementFilterJson> statements();

    /**
     * @return True if sitelinks of included entities should be written to the dump.
     */
    @JsonProperty
    public abstract boolean sitelinks();

    /**
     * @return True if labels of included entities should be written to the dump.
     */
    @JsonProperty
    public abstract boolean labels();

    /**
     * @return True if descriptions of included entities should be written to the dump.
     */
    @JsonProperty
    public abstract boolean descriptions();

    /**
     * @return True if aliases of included entities should be written to the dump.
     */
    @JsonProperty
    public abstract boolean aliases();

    /**
     * @return The set of languages in which labels and descriptions are dumped. If null, include all languages.
     */
    @JsonProperty
    public abstract @Nullable ImmutableSet<String> languages();

    /**
     * @return True if owl declarations and other schema informationshould be written to the dump.
     */
    @JsonProperty
    public abstract boolean meta();

    public static Builder builder() {
        return new AutoValue_DumpSpecJson.Builder()
                .version(DumpSpecVersion.VERSION_1);
    }

    @AutoValue.Builder
    @JsonPOJOBuilder(withPrefix = "", buildMethodName = "jsonBuild")
    public abstract static class Builder {
        public abstract Builder entities(List<EntityFilterJson> entities);

        public abstract Builder samplingPercent(int samplingPercent);

        public abstract Builder seed(long seed);

        public abstract Builder statements(List<StatementFilterJson> statements);

        public abstract Builder sitelinks(boolean sitelinks);

        public abstract Builder labels(boolean labels);

        public abstract Builder descriptions(boolean descriptions);

        public abstract Builder aliases(boolean aliases);

        public abstract Builder languages(Set<String> languages);

        public abstract Builder meta(boolean meta);

        public abstract Builder version(DumpSpecVersion version);

        abstract DumpSpecJson autoBuild();

        public DumpSpecJson build() {
            final DumpSpecJson spec = autoBuild();
            Validate.inclusiveBetween(0, 100, spec.samplingPercent(),
                    "sampling percentage (%s) is not between 0 and 100", spec.samplingPercent());
            return spec;
        }

        // old versions used to have a 'truthy: false' key
        @JsonProperty
        private Builder truthy(boolean value) {
            if (value) {
                throw new IllegalArgumentException("truthy: true was never supported");
            }
            return this;
        }

        public abstract OptionalLong seed();

        public abstract OptionalInt samplingPercent();

        public abstract Optional<DumpSpecVersion> version();

        /**
         * Builder for deserialization from json, performing some migrations
         */
        DumpSpecJson jsonBuild() {
            // set default for old versions that didn't implement sampling
            if (seed().isEmpty() && samplingPercent().isEmpty()) {
                samplingPercent(100);
                seed(0); // if we don't sample, seed doesn't matter
            }

            if (version().isEmpty()) {
                version(DumpSpecVersion.VERSION_1);
            }

            return build();
        }
    }
}