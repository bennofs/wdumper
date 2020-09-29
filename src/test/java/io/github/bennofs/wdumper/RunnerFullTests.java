package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bennofs.wdumper.ext.ZstdDumpFile;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.model.DumpError;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.spec.DumpSpecJson;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wikidata.wdtk.dumpfiles.MwLocalDumpFile;
import org.wikidata.wdtk.rdf.PropertyRegister;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests that generate full RDF dumps.
 */
@Integration
public class RunnerFullTests {
    DumpRunner runner;
    @TempDir Path tempDir;

    static final Logger logger = Logger.getLogger(RunnerFullTests.class.getName());

    @BeforeAll
    static void initWikidataRegister() {
        try {
            PropertyRegister.getWikidataPropertyRegister().fetchUsingSPARQL(new URI("https://query.wikidata.org/sparql"));
        } catch (URISyntaxException e) {
            System.out.println("failed to fetch property info");
            e.printStackTrace();
        }
    }

    @BeforeEach
    void initRunner() {
        final MwLocalDumpFile dump = new ZstdDumpFile("data/slice.json.zst");
        runner = DumpRunner.create(1, new DumpRunner.Config() {
            @Override
            public Path dumpStorageDirectory() {
                return tempDir;
            }

            @Override
            public Duration runProgressInterval() {
                return Duration.of(1, ChronoUnit.SECONDS);
            }
        }, dump);
    }

    private static InputStream openFileStream(Path path) throws IOException {
        if (path.toString().endsWith(".gz")) {
            return new GzipCompressorInputStream(Files.newInputStream(path));
        } else if (path.toString().endsWith(".bz2")) {
            return new BZip2CompressorInputStream(Files.newInputStream(path));
        } else {
            return Files.newInputStream(path);
        }
    }

    @AfterEach
    void testGolden() throws IOException {
        try {
            Files.createDirectory(Path.of("data/generated"));
        } catch (FileAlreadyExistsException ignored) {
        }

        Files.list(tempDir).forEach(path -> {
            try {
                final Path target = Path.of("data/generated/" + path.getFileName());
                if (!Files.exists(target)) {
                    logger.log(Level.WARNING, "target file " + path.getFileName() + " does not exist, copying test output");
                    Files.copy(path, target);
                } else {
                    assertThat(openFileStream(path)).hasSameContentAs(openFileStream(target));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

    }

    void runDump(String specFilePath) throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final Path specPath = Path.of(specFilePath);

        // apply schema migrations
        final DumpSpecJson specJson = mapper.readValue(specPath.toFile(), DumpSpecJson.class);
        final DumpSpec spec = mapper.convertValue(specJson, DumpSpec.class);

        runner.addDumpTask(1, spec, (level, message) -> {
            switch (level) {
                case CRITICAL:
                    logger.log(Level.SEVERE, message);
                    break;
                case ERROR:
                    logger.log(Level.SEVERE, message);
                    break;
                case WARNING:
                    logger.log(Level.INFO, message);
                    break;
            }
        });
        runner.run(new RunnerStatusHandler() {
            @Override
            public void start() {

            }

            @Override
            public void reportProgress(int count) {
                System.out.println(count);
            }

            @Override
            public void done() {

            }
        });
        final Path target = tempDir.resolve(specPath.getFileName().toString().replace(".json", ".nt.gz"));
        Files.move(tempDir.resolve("wdump-1.nt.gz"), target);
    }

    @Test
    void testEmpty() throws IOException {
        runDump("examples/nothing.json");
    }

    @Test
    void testEnglishLabels() throws IOException {
        runDump("examples/english-labels.json");
    }

    @Test
    void testHumans() throws IOException {
        runDump("examples/humans.json");
    }

    @Test
    void testPoliticians() throws IOException {
        runDump("examples/politicians.json");
    }

    @Test
    void testParallel() throws IOException {

    }
}
