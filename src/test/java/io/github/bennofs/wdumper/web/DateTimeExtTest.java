package io.github.bennofs.wdumper.web;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import io.github.bennofs.wdumper.templating.CompositeCollector;
import io.github.bennofs.wdumper.templating.DateTimeExt;
import io.github.bennofs.wdumper.templating.TimeFormatting;
import io.github.bennofs.wdumper.model.Dump;
import io.github.bennofs.wdumper.model.ModelExtension;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class DateTimeExtTest {
    final Mustache.Compiler compiler;

    public DateTimeExtTest() {
        final Mustache.Collector collector = CompositeCollector.builder()
                .addExtension(ModelExtension.class, ModelExtension::extensionBase)
                .addExtension(Instant.class, t -> new DateTimeExt(t, new TimeFormatting()))
                .build();
        this.compiler = Mustache.compiler().withCollector(collector);
    }

    private static final Dump EXAMPLE = Dump.builder()
            .id(1)
            .title("example")
            .description("example")
            .compressedSize(0L)
            .spec("")
            .tripleCount(0L)
            .entityCount(0L)
            .statementCount(0L)
            .createdAt(Instant.parse("2020-06-05T15:40:10Z"))
            .build();

    public static class DumpExt implements ModelExtension {
        @Override
        public Object extensionBase() {
            return EXAMPLE;
        }
    }

    @Test
    void testModelTimestamp() {
        final Template template = compiler.compile("{{createdAt.instant}}");
        assertEquals("2020-06-05T15:40:10Z", template.execute(new DumpExt()));
    }
}
