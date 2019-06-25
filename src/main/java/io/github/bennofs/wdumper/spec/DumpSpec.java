package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.wikidata.wdtk.datamodel.interfaces.StatementDocument;

import java.util.Set;

public class DumpSpec {
    final private EntityFilter[] extractEntities;
    final private Set<String>  extractProperties;
    final private Set<String> extractLanguages;

    final private boolean truthy;
    final private boolean references;
    final private boolean qualifiers;
    final private boolean meta;
    final private boolean sitelinks;

    @JsonIgnore
    final private RDFFormat format = RDFFormat.NTRIPLES;

    @JsonCreator
    public DumpSpec(
            @JsonProperty("extractEntities") EntityFilter[] extractEntities,
            @JsonProperty("extractProperties") Set<String> extractProperties,
            @JsonProperty("extractLanguages") Set<String> extractLanguages,
            @JsonProperty(value = "truthy", defaultValue = "false") boolean truthy,
            @JsonProperty(value = "references", defaultValue = "true") boolean references,
            @JsonProperty(value = "qualifiers", defaultValue = "true") boolean qualifiers,
            @JsonProperty(value = "meta", defaultValue = "true") boolean meta,
            @JsonProperty(value = "sitelinks", defaultValue = "true") boolean sitelinks
    ) {
        this.extractEntities = extractEntities;
        this.extractProperties = extractProperties;
        this.extractLanguages = extractLanguages;
        this.truthy = truthy;
        this.references = references;
        this.qualifiers = qualifiers;
        this.meta = meta;
        this.sitelinks = sitelinks;
    }

    public boolean isTruthy() {
        return truthy;
    }

    public boolean isReferences() {
        return references;
    }

    public boolean isQualifiers() {
        return qualifiers;
    }

    public boolean isMeta() {
        return meta;
    }

    public boolean isSitelinks() {
        return sitelinks;
    }

    public RDFFormat getFormat() {
        return format;
    }

    public boolean includeDocument(StatementDocument doc) {
        if (extractEntities == null) return true;

        for (EntityFilter filterSpec : extractEntities) {
            if (filterSpec.matches(doc)) return true;
        }

        return false;
    }

    public boolean includeLanguage(String code) {
        if (extractLanguages == null) return true;

        return extractLanguages.contains(code);
    }

    public boolean includeProperty(String id) {
        if (extractProperties == null) return true;

        return extractProperties.contains(id);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("extractEntities", extractEntities)
                .add("extractProperties", extractProperties)
                .add("extractLanguages", extractLanguages)
                .add("truthy", truthy)
                .add("references", references)
                .add("qualifiers", qualifiers)
                .add("meta", meta)
                .add("sitelinks", sitelinks)
                .toString();
    }
}
