package io.github.bennofs.wdumper.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A dump with full additional joined information, like run, errors and zenodo status.
 */
public class DumpFullInfo implements ModelExtension {
    public final Dump dump;

    public static class WithBackref<T> implements ModelExtension {
        @JsonUnwrapped
        public final T value;

        @JsonIgnore
        final DumpFullInfo dump;

        public WithBackref(DumpFullInfo dump, T value) {
            this.value = value;
            this.dump = dump;
        }

        @Override
        public Object extensionBase() {
            return value;
        }
    }

    public final @Nullable WithBackref<Run> run;
    public final @Nullable WithBackref<Zenodo> zenodoSandbox;
    public final @Nullable WithBackref<Zenodo> zenodoRelease;
    public final ImmutableList<WithBackref<DumpError>> errors;

    public DumpRunZenodo toDumpRunZenodo() {
        return new DumpRunZenodo(dump,
                run == null ? null : run.value,
                zenodoSandbox == null ? null : zenodoSandbox.value,
                zenodoRelease == null ? null : zenodoRelease.value
        );
    }

    private <T> WithBackref<T> withBackref(T v) {
        if (v == null) return null;
        return new WithBackref<>(this, v);
    }

    public DumpFullInfo(Dump dump, @Nullable Run run, @Nullable Zenodo zenodoSandbox, @Nullable Zenodo zenodoRelease, List<DumpError> errors) {
        this.dump = dump;
        this.run = withBackref(run);
        this.zenodoSandbox = withBackref(zenodoSandbox);
        this.zenodoRelease = withBackref(zenodoRelease);
        this.errors = errors.stream().map(this::withBackref).collect(ImmutableList.toImmutableList());
    }

    @Override
    public Object extensionBase() {
        return toDumpRunZenodo();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
