package io.github.bennofs.wdumper.spec;

import com.google.common.base.MoreObjects;
import org.wikidata.wdtk.datamodel.interfaces.StatementRank;

public class StatementOptions {
    private final boolean simple;
    private final boolean full;
    private final boolean references;
    private final boolean qualifiers;
    private final RankFilter rank;

    public StatementOptions(RankFilter rank, boolean simple, boolean full, boolean references, boolean qualifiers) {
        this.simple = simple;
        this.full = full;
        this.references = references;
        this.qualifiers = qualifiers;
        this.rank = rank;
    }

    public boolean isSimple() {
        return simple;
    }

    public boolean isFull() {
        return full;
    }

    public boolean isReferences() {
        return references;
    }

    public boolean isQualifiers() {
        return qualifiers;
    }

    public boolean isStatement() {
        return this.full || this.references || this.qualifiers;
    }

    public RankFilter getRankFilter() {
        return rank;
    }

    public StatementOptions union(StatementOptions other) {
        if (other == null) return this;

        return new StatementOptions(rank.union(other.rank), simple || other.simple, full || other.full, references || other.references, qualifiers || other.qualifiers);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("simple", simple)
                .add("full", full)
                .add("references", references)
                .add("qualifiers", qualifiers)
                .toString();
    }
}
