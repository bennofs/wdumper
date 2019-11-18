package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.MoreObjects;

import java.util.Objects;
import java.util.Set;

@JsonIgnoreProperties({"id"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatementFilter {
    private final Set<String> properties;
    private final StatementOptions options;

    @JsonCreator
    public StatementFilter(
            @JsonProperty(value = "properties") Set<String> properties,
            @JsonProperty(value = "simple") boolean simple,
            @JsonProperty(value = "full") boolean full,
            @JsonProperty(value = "references") boolean references,
            @JsonProperty(value = "qualifiers") boolean qualifiers
    ) {
        this.properties = properties;
        this.options = new StatementOptions(simple, full, references, qualifiers);
    }

    public Set<String> getProperties() {
        return properties;
    }

    public StatementOptions getOptions() {
        return options;
    }
}
