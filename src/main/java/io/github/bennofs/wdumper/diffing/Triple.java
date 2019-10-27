package io.github.bennofs.wdumper.diffing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ByteBufferSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Triple {
    @JsonSerialize(using=ByteBufferStringSerializer.class)
    public final ByteBuffer subject;

    @JsonSerialize(using=ByteBufferStringSerializer.class)
    public final ByteBuffer predicate;

    @JsonSerialize(using=ByteBufferStringSerializer.class)
    public final ByteBuffer object;

    @JsonCreator
    public Triple(
            @JsonProperty("subject")
            @JsonDeserialize(using=ByteBufferStringDeserializer.class)
            ByteBuffer subject,
            @JsonProperty("predicate")
            @JsonDeserialize(using=ByteBufferStringDeserializer.class)
            ByteBuffer predicate,
            @JsonProperty("object")
            @JsonDeserialize(using=ByteBufferStringDeserializer.class)
            ByteBuffer object) {
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

    private static final Pattern POINT_PATTERN = Pattern.compile("\"(<[^>]*> )?Point\\(([^ ]*) ([^ ]*)\\)\"\\^\\^<c/wkt>");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("\"([^\"]*)\"\\^\\^<c/double>");

    public boolean approxEquals(Triple other) {
        boolean baseEquals = this.subject.equals(other.subject) && this.predicate.equals(other.predicate);
        if (!baseEquals) return false;

        String thisObj = Utils.bufferString(this.object);
        String otherObj = Utils.bufferString(other.object);

        final Matcher thisPoint = POINT_PATTERN.matcher(thisObj);
        final Matcher otherPoint = POINT_PATTERN.matcher(otherObj);
        if (thisPoint.matches() && otherPoint.matches()) {
            final String thisPrefix = thisPoint.group(1);
            final String thisF1 = thisPoint.group(2);
            final String thisF2 = thisPoint.group(3);

            final String otherPrefix = otherPoint.group(1);
            final String otherF1 = otherPoint.group(2);
            final String otherF2 = otherPoint.group(3);

            return Objects.equals(thisPrefix, otherPrefix) && floatEqual(thisF1, otherF1) && floatEqual(thisF2, otherF2);
        }

        final Matcher thisDouble = DOUBLE_PATTERN.matcher(thisObj);
        final Matcher otherDouble = DOUBLE_PATTERN.matcher(otherObj);
        if (thisDouble.matches() && otherDouble.matches()) {
            return floatEqual(thisDouble.group(1), otherDouble.group(1));
        }

        return thisObj.equals(otherObj);
    }

    private static boolean floatEqual(String a, String b) {
        try {
            if (a.equals(b)) return true;

            double da = Double.parseDouble(a);
            double db = Double.parseDouble(b);
            return Math.abs(da - db) <= 1.1;
        } catch (NumberFormatException e) {
            return a.equals(b);
        }
    }

    private static class ByteBufferStringSerializer extends JsonSerializer<ByteBuffer> {
        @Override
        public void serialize(ByteBuffer value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(Utils.bufferString(value));
        }
    }
}
