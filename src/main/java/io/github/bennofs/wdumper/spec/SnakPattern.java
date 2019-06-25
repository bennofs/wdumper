package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.NotImplementedException;
import org.wikidata.wdtk.datamodel.implementation.SnakImpl;
import org.wikidata.wdtk.datamodel.interfaces.*;

public class SnakPattern {
    private final Snak pattern;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SnakPattern(SnakImpl pattern) {
        this.pattern = pattern;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("pattern", pattern)
                .toString();
    }

    boolean matches(StatementDocument doc) {
        if (pattern != null) {
            final PropertyIdValue propertyId = pattern.getPropertyId();
            final StatementGroup sg = doc.findStatementGroup(propertyId.getId());

            return pattern.accept(new SnakVisitor<>() {
                @Override
                public Boolean visit(ValueSnak snak) {
                    if (sg == null || sg.size() == 0) return false;
                    for (final Statement stmt : sg.getStatements()) {
                        final Value value = stmt.getValue();
                        if (value != null && value.equals(snak.getValue())) return true;
                    }
                    return false;
                }

                @Override
                public Boolean visit(SomeValueSnak snak) {
                    return sg != null && sg.size() > 0;
                }

                @Override
                public Boolean visit(NoValueSnak snak) {
                    throw new NotImplementedException("TODO"); // TODO
                }
            });
        }

        return true;
    }
}
