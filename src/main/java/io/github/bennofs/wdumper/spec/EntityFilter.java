package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wikidata.wdtk.datamodel.interfaces.*;

import java.util.Map;

import static org.wikidata.wdtk.datamodel.interfaces.EntityIdValue.*;

public class EntityFilter {
    private enum EntityType {
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

    private final EntityType type;
    private final Map<String, ValueFilter> properties;

    @JsonCreator
    EntityFilter(
            @JsonProperty(value = "type", required = true) EntityType type,
            @JsonProperty(value = "properties") Map<String, ValueFilter> properties
    ) {
        this.type = type;
        this.properties = properties;
    }

    boolean matches(StatementDocument doc) {
        if (!this.type.matches(doc.getEntityId())) return false;

        for (Map.Entry<String, ValueFilter> filter : this.properties.entrySet()) {
            StatementGroup sg = doc.findStatementGroup(filter.getKey());

            if (!filter.getValue().matches(sg)) return false;
        }

        return true;
    }
}
