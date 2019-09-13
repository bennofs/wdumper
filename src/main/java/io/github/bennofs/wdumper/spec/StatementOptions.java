package io.github.bennofs.wdumper.spec;

import com.google.common.base.MoreObjects;

public class StatementOptions {
    private final boolean simple;
    private final boolean full;
    private final boolean references;
    private final boolean qualifiers;

    public StatementOptions() {
        this(false, false, false, false);
    }

    public StatementOptions(boolean simple, boolean full, boolean references, boolean qualifiers) {
        this.simple = simple;
        this.full = full;
        this.references = references;
        this.qualifiers = qualifiers;
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

    public StatementOptions union(StatementOptions other) {
        if (other == null) return this;

        return new StatementOptions(
                this.simple || other.simple,
                this.full || other.full,
                this.references || other.references,
                this.qualifiers || other.qualifiers
        );
    }

    public boolean isStatement() {
        return this.full || this.references || this.qualifiers;
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
