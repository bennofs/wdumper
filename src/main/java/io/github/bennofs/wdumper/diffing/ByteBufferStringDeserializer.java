package io.github.bennofs.wdumper.diffing;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ByteBufferStringDeserializer extends JsonDeserializer<ByteBuffer> {
    @Override
    public ByteBuffer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        String value = p.getValueAsString();
        if (value == null) {
            throw new IOException("expected string");
        }
        return ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));
    }
}
