package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.Validate;
import org.wikidata.wdtk.datamodel.interfaces.*;

@JsonIgnoreProperties({"id"})
public class ValueFilter implements SnakVisitor<Boolean> {
    public enum ValueFilterType {
        @JsonProperty("novalue") NO_VALUE,
        @JsonProperty("somevalue") SOME_VALUE,
        @JsonProperty("entityid") ENTITYID,
        @JsonProperty("anyvalue") ANY_VALUE,
        @JsonProperty("any") ANY
    }

    private final String property;
    private final ValueFilterType type;
    private final String value;
    private final boolean truthy;

    @JsonCreator
    ValueFilter(
            @JsonProperty(value = "property", required = true) String property,
            @JsonProperty(value = "type", required = true) ValueFilterType type,
            @JsonProperty(value = "value") String value,
            @JsonProperty(value = "truthy", required = true) boolean truthy
    ) {
        this.property = property;
        this.type = type;
        this.value = value;
        this.truthy = truthy;

        if (this.type == ValueFilterType.ENTITYID) {
            Validate.notNull(this.value, "filter with type entityid requires value attribute");
        }
    }

    @Override
    public Boolean visit(ValueSnak snak) {
        if (this.type == ValueFilterType.ANY || this.type == ValueFilterType.ANY_VALUE) return true;
        if (this.type != ValueFilterType.ENTITYID) return false;

        final Value foundValue = snak.getValue();
        return foundValue.accept(new ValueVisitor<Boolean>() {
            @Override
            public Boolean visit(EntityIdValue value) {
                final String id = value.getId();
                return id.equals(ValueFilter.this.value);
            }

            @Override
            public Boolean visit(GlobeCoordinatesValue value) {
                return false;
            }

            @Override
            public Boolean visit(MonolingualTextValue value) {
                return false;
            }

            @Override
            public Boolean visit(QuantityValue value) {
                return false;
            }

            @Override
            public Boolean visit(StringValue value) {
                return false;
            }

            @Override
            public Boolean visit(TimeValue value) {
                return false;
            }
        });
    };

    @Override
    public Boolean visit(SomeValueSnak snak) {
        return this.type == ValueFilterType.ANY || this.type == ValueFilterType.SOME_VALUE;
    }

    @Override
    public Boolean visit(NoValueSnak snak) {
        return this.type == ValueFilterType.ANY || this.type == ValueFilterType.NO_VALUE;
    }

    boolean matches(StatementGroup sg) {
        if (sg == null) return false;

        if (this.truthy) {
            sg = sg.getBestStatements();
            if (sg == null) return false;
        }

        for (Statement s : sg) {
            if (s.getMainSnak().accept(this)) return true;
        }

        return false;
    }

    public String getProperty() {
        return property;
    }
}
