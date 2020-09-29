package io.github.bennofs.wdumper.model;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A dump together with the run and zenodo records associated to it.
 */
public class DumpRunZenodo implements ModelExtension {
    public final Dump dump;
    public final @Nullable Run run;
    public final @Nullable Zenodo zenodoSandbox;
    public final @Nullable Zenodo zenodoRelease;

    public DumpRunZenodo(Dump dump, @Nullable Run run, @Nullable Zenodo zenodoSandbox, @Nullable Zenodo zenodoRelease) {
        this.dump = dump;
        this.run = run;
        this.zenodoSandbox = zenodoSandbox;
        this.zenodoRelease = zenodoRelease;
    }

    @Override
    public Object extensionBase() {
        return dump;
    }

    public Optional<Instant> startedAt() {
        return Optional.ofNullable(this.run).flatMap(Run::startedAt);
    }

    public Optional<Instant> finishedAt() {
        return Optional.ofNullable(this.run).flatMap(Run::finishedAt);
    }

    public Optional<Instant> isQueued() {
        if (this.run == null || this.run.startedAt().isEmpty()) {
            return Optional.of(this.dump.createdAt());
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DumpRunZenodo that = (DumpRunZenodo) o;
        return dump.equals(that.dump) &&
                Objects.equals(run, that.run) &&
                Objects.equals(zenodoSandbox, that.zenodoSandbox) &&
                Objects.equals(zenodoRelease, that.zenodoRelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dump, run, zenodoSandbox, zenodoRelease);
    }
}
