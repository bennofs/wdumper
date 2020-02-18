package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;

import static org.wikidata.wdtk.datamodel.interfaces.EntityIdValue.*;

/**
 * An enum of possible ways to filter by type of entity (property, item, etc)
 */
public enum EntityTypeFilter {
    @JsonProperty("property") PROPERTY,
    @JsonProperty("item") ITEM,
    @JsonProperty("lexeme") LEXEME,
    @JsonProperty("any") ANY;

    boolean matches(EntityIdValue entity) {
        switch (this) {
            case PROPERTY:
                return entity.getEntityType().equals(ET_PROPERTY);
            case ITEM:
                return entity.getEntityType().equals(ET_ITEM);
            case LEXEME:
                return entity.getEntityType().equals(ET_LEXEME);
            case ANY:
                return true;
        }

        throw new RuntimeException("EntityFilter enum has unexpected value: " + this);
    }
}
