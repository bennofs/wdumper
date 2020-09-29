package io.github.bennofs.wdumper.database;

import java.util.Objects;

public class DumpTask {
    public final int id;
    public final String spec;

    DumpTask(int id, String spec) {
        Objects.requireNonNull(spec);

        this.id = id;
        this.spec = spec;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DumpTask dumpTask = (DumpTask) o;
        return id == dumpTask.id &&
                spec.equals(dumpTask.spec);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, spec);
    }

    @Override
    public String toString() {
        return "DumpTask{" +
                "id=" + id +
                ", spec='" + spec + '\'' +
                '}';
    }
}
