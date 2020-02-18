package io.github.bennofs.wdumper.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class DumpSpecTest {
    @Test
    public void basicParserTest() throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
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
                .seed(132)
                .samplingPercent(90)
                .build();
        assertEquals(expectedSpec, spec);
    }

    @Test
    public void illegalValuesTest() throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
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
        final ObjectMapper mapper = new ObjectMapper();
        final InputStream stream = getClass().getResourceAsStream("/simple-spec.json");
        final ObjectNode origNode = mapper.readValue(stream, ObjectNode.class);
        final DumpSpecJson spec = mapper.convertValue(origNode, DumpSpecJson.class);

        final var nodeNoVersion = origNode.deepCopy().without("version");
        assertEquals(spec, mapper.convertValue(nodeNoVersion, DumpSpecJson.class));

        final var nodeNoSampling = origNode.deepCopy().without(List.of("seed", "samplingPercent"));
        assertEquals(100, mapper.convertValue(nodeNoSampling, DumpSpecJson.class).samplingPercent());
    }
}
