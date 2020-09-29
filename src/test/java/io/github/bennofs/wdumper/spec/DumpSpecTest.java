package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DumpSpecTest {
    private static final ObjectMapper createObjectMapper() {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new ParameterNamesModule());
        return objectMapper;
    }

    @Test
    public void basicParserTest() throws IOException {
        final ObjectMapper mapper = createObjectMapper();
        final InputStream stream = getClass().getResourceAsStream("/simple-spec.json");
        final DumpSpecJson spec = mapper.readValue(stream, DumpSpecJson.class);

        final EntityFilterJson expectedFilter = EntityFilterJson.builder()
                .type(EntityTypeFilter.ITEM)
                .properties(Set.of(
                        PropertyRestrictionJson.builder()
                                .type(PropertyRestrictionJson.Type.ENTITYID)
                                .rank(RankFilter.NON_DEPRECATED)
                                .value("Q101352")
                                .property("P31")
                                .build()
                ))
                .build();
        final DumpSpecJson expectedSpec = DumpSpecJson.builder()
                .version(DumpSpecVersion.VERSION_1)
                .sitelinks(false)
                .descriptions(false)
                .entities(List.of(expectedFilter))
                .statements(List.of(StatementFilterJson.builder()
                        .simple(true)
                        .rank(RankFilter.ALL)
                        .references(false)
                        .full(false)
                        .qualifiers(false)
                        .build()))
                .aliases(false)
                .meta(true)
                .labels(true)
                .seed(132L)
                .samplingPercent(90)
                .build();
        assertEquals(expectedSpec, spec);
    }

    @Test
    public void illegalValuesTest() throws IOException {
        final ObjectMapper mapper = createObjectMapper();
        final InputStream stream = getClass().getResourceAsStream("/simple-spec.json");
        final ObjectNode origNode = mapper.readValue(stream, ObjectNode.class);

        assertThrows(IllegalArgumentException.class, () ->
                mapper.convertValue(origNode.deepCopy().without("seed"), DumpSpecJson.class));
        assertThrows(IllegalArgumentException.class, () ->
                mapper.convertValue(origNode.deepCopy().without("aliases"), DumpSpecJson.class));
        assertThrows(IllegalArgumentException.class, () ->
                mapper.convertValue(origNode.deepCopy().without("statements"), DumpSpecJson.class));
        assertThrows(IllegalArgumentException.class, () ->
                mapper.convertValue(origNode.deepCopy().without("entities"), DumpSpecJson.class));
        assertThrows(IllegalArgumentException.class, () -> {
            final ObjectNode wrongSampleRate = origNode.deepCopy();
            wrongSampleRate.put("samplingPercent", 120);
            mapper.convertValue(wrongSampleRate, DumpSpecJson.class);
        });
    }

    @Test
    public void migrationTest() throws IOException {
        final ObjectMapper mapper = createObjectMapper();
        final InputStream stream = getClass().getResourceAsStream("/simple-spec.json");
        final ObjectNode origNode = mapper.readValue(stream, ObjectNode.class);
        final DumpSpecJson spec = mapper.convertValue(origNode, DumpSpecJson.class);

        final var nodeNoVersion = origNode.deepCopy().without("version");
        assertEquals(spec, mapper.convertValue(nodeNoVersion, DumpSpecJson.class));

        final var nodeNoSampling = origNode.deepCopy().without(List.of("seed", "samplingPercent"));
        assertEquals(100, mapper.convertValue(nodeNoSampling, DumpSpecJson.class).samplingPercent());
    }

    @Test
    public void jsonRoundtripTest() throws IOException {
        final ObjectMapper mapper = createObjectMapper();
        final InputStream stream = getClass().getResourceAsStream("/simple-spec.json");
        final ObjectNode origNode = mapper.readValue(stream, ObjectNode.class);

        // basic roundtrip test
        final var origSpec = mapper.convertValue(origNode, DumpSpecJson.class);
        assertEquals(origSpec, mapper.readValue(mapper.writeValueAsBytes(origSpec), DumpSpecJson.class));

        // roundtrip if seed is still unspecified
        origNode.set("samplingPercent", new LongNode(23)); // some value != 100 so we don't trigger migration
        final var specWithoutSeed = mapper.convertValue(origNode, DumpSpecJson.class);
        assertEquals(specWithoutSeed, mapper.readValue(mapper.writeValueAsBytes(specWithoutSeed), DumpSpecJson.class));
    }
}
