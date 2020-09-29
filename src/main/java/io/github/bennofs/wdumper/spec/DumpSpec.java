package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.MoreObjects;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.wikidata.wdtk.datamodel.interfaces.StatementDocument;

import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DumpSpec {
    @JsonProperty
    final private EntityFilter[] entities;
    @JsonProperty
    final private Set<StatementFilter> statements;
    final private HashMap<String, StatementOptions> statementOptions;
    private final StatementOptions statementOptionsDefault;
    @JsonProperty
    final private Set<String> languages;

    final private String version;
    final private boolean meta;
    final private boolean labels;
    final private boolean descriptions;
    final private boolean aliases;
    final private boolean sitelinks;
    final private int samplingPercent;
    final private long seed;

    @JsonIgnore
    final private RDFFormat format = RDFFormat.NTRIPLES;

    @JsonIgnore
    final private Random random;

    @JsonCreator
    public DumpSpec(
            @JsonProperty("version") String version,
            @JsonProperty("entities") EntityFilter[] entities,
            @JsonProperty("statements") Set<StatementFilter> statements,
            @JsonProperty(value = "samplingPercent") Integer samplingPercent,
            @JsonProperty(value = "seed") Long seed,
            @JsonProperty("languages") Set<String> languages,
            @JsonProperty(value = "labels") boolean labels,
            @JsonProperty(value = "descriptions") boolean descriptions,
            @JsonProperty(value = "aliases") boolean aliases,
            @JsonProperty(value = "meta", defaultValue = "true") boolean meta,
            @JsonProperty(value = "sitelinks", defaultValue = "true") boolean sitelinks
    ) {
        Objects.requireNonNull(entities);
        Objects.requireNonNull(statements);

        this.version = version;
        this.entities = entities;
        this.statements = statements;
        this.samplingPercent = samplingPercent == null ? 100 : samplingPercent;
        this.languages = languages;
        this.labels = labels;
        this.descriptions = descriptions;
        this.aliases = aliases;
        this.meta = meta;
        this.sitelinks = sitelinks;

        if (seed == null) {
            seed = ThreadLocalRandom.current().nextLong();
        }
        this.seed = seed;
        this.random = new Random(seed);

        this.statementOptions = new HashMap<>();
        this.statementOptionsDefault = statements.stream()
                .filter(f -> f.getProperties() == null)
                .map(StatementFilter::getOptions)
                .reduce(StatementOptions::union)
                .orElse(new StatementOptions(RankFilter.BEST_RANK, false, false, false, false));

        for (StatementFilter statementFilter : statements) {
            if (statementFilter.getProperties() == null) {
                continue;
            }

            for (final String property : statementFilter.getProperties()) {
                final StatementOptions options = statementFilter.getOptions().union(this.statementOptions.get(property));
                this.statementOptions.put(property, options);
            }
        }
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
        boolean include = entities.length == 0;
        for (EntityFilter filterSpec : entities) {
            if (filterSpec.matches(doc)) {
                include = true;
            }
        }

        include = include &&
                (this.samplingPercent == 100 || this.random.nextInt(100) < this.samplingPercent);

        return include;
    }

    public StatementOptions findStatementOptions(final String property) {
        return statementOptions.getOrDefault(property, statementOptionsDefault);
    }

    public boolean hasFullStatements() {
        return statementOptionsDefault.isStatement() || statementOptions.values().stream().anyMatch(StatementOptions::isStatement);
    }

    public boolean includeLanguage(String code) {
        if (languages == null) return true;

        return languages.contains(code);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("version", version)
                .add("entities", entities)
                .add("statementOptions", statementOptions)
                .add("languages", languages)
                .add("samplingPercent", samplingPercent)
                .add("seed", seed)
                .add("labels", labels)
                .add("descriptions", descriptions)
                .add("aliases", aliases)
                .add("meta", meta)
                .add("sitelinks", sitelinks)
                .toString();
    }
}
