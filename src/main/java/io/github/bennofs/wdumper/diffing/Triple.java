package io.github.bennofs.wdumper.diffing;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ByteBufferSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Triple {
    @JsonSerialize(using=ByteBufferStringSerializer.class)
    public final ByteBuffer subject;

    @JsonSerialize(using=ByteBufferStringSerializer.class)
    public final ByteBuffer predicate;

    @JsonSerialize(using=ByteBufferStringSerializer.class)
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

    private static class ByteBufferStringSerializer extends JsonSerializer<ByteBuffer> {
        @Override
        public void serialize(ByteBuffer value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(Utils.bufferString(value));
        }
    }
}
