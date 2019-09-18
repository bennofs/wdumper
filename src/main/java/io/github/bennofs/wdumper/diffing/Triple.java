package io.github.bennofs.wdumper.diffing;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class Triple {
    public final ByteBuffer subject;
    public final ByteBuffer predicate;
    public final ByteBuffer object;

    public Triple(ByteBuffer subject, ByteBuffer predicate, ByteBuffer object) {
        this.subject = subject.asReadOnlyBuffer();
        this.predicate = predicate.asReadOnlyBuffer();
        this.object = object.asReadOnlyBuffer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Triple triple = (Triple) o;
        return subject.equals(triple.subject) &&
                predicate.equals(triple.predicate) &&
                object.equals(triple.object);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, predicate, object);
    }

    public String toString() {
        return Utils.bufferString(subject) + " " + Utils.bufferString(predicate) + " " + Utils.bufferString(object) + " .";
    }
}
