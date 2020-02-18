package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;
import org.apache.commons.lang3.Validate;
import org.wikidata.wdtk.datamodel.interfaces.*;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A filter condition based on the existence or value of statement for a given property.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_PropertyRestrictionJson.Builder.class)
public abstract class PropertyRestrictionJson {

    /**
     * Types of value filter conditions on statements.
     */
    public enum Type {
        /**
         * The statement has the special value NoValue as its value
         */
        @JsonProperty("novalue") NO_VALUE,

        /**
         * The statement has special value SomeValue as its value
         */
        @JsonProperty("somevalue") SOME_VALUE,

        /**
         * The statement has a specific entity id as its value.
         */
        @JsonProperty("entityid") ENTITYID,

        /**
         * The statement is present and has some specific value (not NoValue or SomeValue)
         */
        @JsonProperty("anyvalue") ANY_VALUE,

        /**
         * The statement is present, value is arbitrary (can also be NoValue or SomeValue)
         */
        @JsonProperty("any") ANY
    }

    /**
     * Property that the statements matched by this filter need to have.
     */
    @JsonProperty
    public abstract String property();

    /**
     * Rank filter that needs to be satisfied by the statements matched by this filter.
     */
    @JsonProperty
    public abstract RankFilter rank();

    /**
     * Type of this filter.
     */
    @JsonProperty
    public abstract Type type();

    /**
     * Check if any statement in the given statement group matches this filter.
     *
     * @param sg The statement group against which to check the filter
     * @return True if any statement is matched by this property restriction.
     */
    public boolean matches(StatementGroup sg) {
        if (sg == null) return false;

        if (rank() == RankFilter.BEST_RANK) {
            sg = sg.getBestStatements();
            if (sg == null) return false;
        }

        return sg.stream().anyMatch(s -> {
            if (s.getRank() == StatementRank.DEPRECATED && rank() != RankFilter.ALL) return false;
            if (type() == Type.ANY) return true;

            return s.getMainSnak().accept(new SnakVisitor<>() {
                @Override
                public Boolean visit(ValueSnak snak) {
                    if (type() == Type.ANY_VALUE) return true;
                    if (type() != Type.ENTITYID) return false;

                    final Value foundValue = snak.getValue();
                    return foundValue instanceof EntityIdValue &&
                        ((EntityIdValue) foundValue).getId().equals(value());
                }

                @Override
                public Boolean visit(SomeValueSnak snak) {
                    return type() == Type.SOME_VALUE;
                }

                @Override
                public Boolean visit(NoValueSnak snak) {
                    return type() == Type.NO_VALUE;
                }
            });
        });
    }

    /**
     * Value that the statement needs to have. Depending on {@link #type()} this may also be null,
     * if the filter type does not require any value.
     */
    @JsonProperty
    public abstract @Nullable String value();

    public static Builder builder() {
        return new AutoValue_PropertyRestrictionJson.Builder();
    }

    @AutoValue.Builder
    @JsonPOJOBuilder(withPrefix = "", buildMethodName = "jsonBuild")
    @JsonIgnoreProperties({"id"})
    public abstract static class Builder {
        public abstract Builder property(String property);

        public abstract Builder rank(RankFilter rank);

        public abstract Builder type(Type type);

        public abstract Builder value(String value);

        abstract PropertyRestrictionJson autoBuild();

        public PropertyRestrictionJson build() {
            final PropertyRestrictionJson result = autoBuild();
            if (result.type() == Type.ENTITYID) {
                Validate.notNull(result.value(), "property restriction with type entityid requires value attribute");
            } else {
                Validate.isTrue(result.value() == null, "property restriction with type %s cannot have a value attribute", result.type().toString());
            }
            return result;
        }

        // old versions used to have a 'truthy: false' key
        @JsonProperty Builder truthy(boolean value) {
            if (value) {
                throw new IllegalArgumentException("truthy: true was never supported");
            }
            return this;
        }

        abstract Optional<RankFilter> rank();

        PropertyRestrictionJson jsonBuild() {
            if (rank().isEmpty()) rank(RankFilter.ALL);

            return build();
        }
    }
}
