package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.wikidata.wdtk.datamodel.interfaces.StatementDocument;

import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DumpSpec {
    @JsonProperty
    final private EntityFilter[] entities;
    @JsonProperty
    final private Set<StatementFilter> statements;
    final private HashMap<String, StatementOptions> statementOptions;
    private StatementOptions statementOptionsAll;
    @JsonProperty
    final private Set<String> languages;

    final private boolean truthy;
    final private boolean meta;
    final private boolean labels;
    final private boolean descriptions;
    final private boolean aliases;
    final private boolean sitelinks;

    @JsonIgnore
    final private RDFFormat format = RDFFormat.NTRIPLES;

    @JsonCreator
    public DumpSpec(
            @JsonProperty("entities") EntityFilter[] entities,
            @JsonProperty("statements") Set<StatementFilter> statements,
            @JsonProperty("languages") Set<String> languages,
            @JsonProperty(value = "labels") boolean labels,
            @JsonProperty(value = "descriptions") boolean descriptions,
            @JsonProperty(value = "aliases") boolean aliases,
            @JsonProperty(value = "truthy", defaultValue = "false") boolean truthy,
            @JsonProperty(value = "meta", defaultValue = "true") boolean meta,
            @JsonProperty(value = "sitelinks", defaultValue = "true") boolean sitelinks
    ) {
        Objects.requireNonNull(entities);
        Objects.requireNonNull(statements);

        this.entities = entities;
        this.statements = statements;
        this.languages = languages;
        this.labels = labels;
        this.descriptions = descriptions;
        this.aliases = aliases;
        this.truthy = truthy;
        this.meta = meta;
        this.sitelinks = sitelinks;

        this.statementOptions = new HashMap<String, StatementOptions>();
        this.statementOptionsAll = new StatementOptions();
        for (StatementFilter statementFilter : statements) {
            if (statementFilter.getProperties() == null) {
                statementOptionsAll = statementOptionsAll.union(statementFilter.getOptions());
                continue;
            }

            for (final String property : statementFilter.getProperties()) {
                final StatementOptions options = statementFilter.getOptions().union(this.statementOptions.get(property));
                this.statementOptions.put(property, options);
            }
        }
    }

    public boolean isTruthy() {
        return truthy;
    }

    public boolean isMeta() {
        return meta;
    }

    public boolean isSitelinks() {
        return sitelinks;
    }

    public boolean isLabels() {
        return labels;
    }

    public boolean isDescriptions() {
        return descriptions;
    }

    public boolean isAliases() {
        return aliases;
    }

    public RDFFormat getFormat() {
        return format;
    }

    public boolean includeDocument(StatementDocument doc) {
        if (entities.length == 0) return true;

        for (EntityFilter filterSpec : entities) {
            if (filterSpec.matches(doc)) return true;
        }

        return false;
    }

    public StatementOptions findStatementOptions(final String property) {
        final StatementOptions specific = statementOptions.getOrDefault(property, new StatementOptions());
        return specific.union(this.statementOptionsAll);
    }

    public boolean includeLanguage(String code) {
        if (languages == null) return true;

        return languages.contains(code);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("entities", entities)
                .add("statementOptions", statementOptions)
                .add("languages", languages)
                .add("labels", labels)
                .add("descriptions", descriptions)
                .add("aliases", aliases)
                .add("truthy", truthy)
                .add("meta", meta)
                .add("sitelinks", sitelinks)
                .toString();
    }
}
