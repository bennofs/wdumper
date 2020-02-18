package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import org.wikidata.wdtk.datamodel.interfaces.StatementDocument;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;

import java.util.Set;

/**
 * An entity filter is a part of a dump specification that describes how to select entities that should be dumped.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_EntityFilterJson.Builder.class)
public abstract class EntityFilterJson {
    /**
     * Specifies the type of entities matched by this filter.
     */
    @JsonProperty
    public abstract EntityTypeFilter type();

    /**
     * Conditions on properties that need to be satisfied to match an entity by this filter.
     */
    @JsonProperty
    public abstract ImmutableSet<PropertyRestrictionJson> properties();

    /**
     * Check if the document matches this filter.
     *
     * @param doc The statement document that should be checked against this filter.
     * @return True if the document should be included according to this filter, false otherwise.
     */
    public boolean matches(StatementDocument doc) {
        return type().matches(doc.getEntityId()) && properties().stream().allMatch(restriction-> {
            final StatementGroup sg = doc.findStatementGroup(restriction.property());
            return restriction.matches(sg);
        });
    }

    public static Builder builder() {
        return new AutoValue_EntityFilterJson.Builder();
    }

    @AutoValue.Builder
    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties({"id"})
    public abstract static class Builder {
        public abstract Builder type(EntityTypeFilter type);

        public abstract Builder properties(Set<PropertyRestrictionJson> properties);

        public abstract EntityFilterJson build();
    }
}
