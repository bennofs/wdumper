package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.wikidata.wdtk.datamodel.interfaces.StatementDocument;

import java.util.List;

public class FilterSpec {
    private final List<SnakPattern> snakPatterns;
    private final String type;

    @JsonCreator
    public FilterSpec(
            @JsonProperty("snakPatterns") List<SnakPattern> snakPatterns,
            @JsonProperty("type") String type
    ) {
        this.snakPatterns = snakPatterns;
        this.type = type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("snakPatterns", snakPatterns)
                .add("type", type)
                .toString();
    }

    boolean matches(StatementDocument doc) {
        if (this.type != null && !doc.getEntityId().getEntityType().equals(this.type)) {
            return false;
        }

        if (this.snakPatterns != null) {
            for (SnakPattern pattern : this.snakPatterns) {
                if (pattern.matches(doc)) return true;
            }

            // no pattern matched
            return false;
        }

        return true;
    }
}
