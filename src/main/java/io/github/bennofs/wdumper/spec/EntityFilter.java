package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.wikidata.wdtk.datamodel.interfaces.StatementDocument;
import org.wikidata.wdtk.datamodel.interfaces.StatementGroup;

import java.util.List;

@JsonIgnoreProperties({"id"})
public class EntityFilter {
    private final EntityTypeFilter type;
    private final List<ValueFilter> properties;

    @JsonCreator
    EntityFilter(
            @JsonProperty(value = "type", required = true) EntityTypeFilter type,
            @JsonProperty(value = "properties") List<ValueFilter> properties
    ) {
        this.type = type;
        this.properties = properties;
    }

    boolean matches(StatementDocument doc) {
        if (!this.type.matches(doc.getEntityId())) return false;

        for (ValueFilter filter : this.properties) {
            StatementGroup sg = doc.findStatementGroup(filter.getProperty());

            if (!filter.matches(sg)) return false;
        }

        return true;
    }
}
