package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bennofs.wdumper.Integration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Integration
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DumpLoadingIntegrationTest {
    private final HashMap<String, byte[]> specs = new HashMap<>();

    @BeforeAll
    public void loadSpecs() throws IOException {
        for (File file : Objects.requireNonNull(new File("data/specs").listFiles())) {
            specs.put("file://" + file.getAbsolutePath(), Files.readAllBytes(file.toPath()));
        }
    }

    /**
     * Test that all the specs parse successfully and roundtrip after migratiion.
     */
    @Test
    public void testParseRoundtrip() throws IOException {
        final ObjectMapper mapper = new ObjectMapper();

        for (Map.Entry<String, byte[]> entry : specs.entrySet()) {
            final DumpSpecJson spec;
            try {
                spec = mapper.readValue(entry.getValue(), DumpSpecJson.class);
            } catch (IOException e) {
                throw new RuntimeException("cannot parse " + entry.getKey(), e);
            }

            final DumpSpecJson reparsed;
            try {
                reparsed = mapper.readValue(mapper.writeValueAsString(spec), DumpSpecJson.class);
            } catch (IOException e) {
                throw new RuntimeException("reparsing " + entry.getKey() + " failed", e);
            }

            assertEquals(spec, reparsed);
            assertEquals(mapper.writeValueAsString(spec), mapper.writeValueAsString(reparsed));
        }
    }
}
