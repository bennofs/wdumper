package io.github.bennofs.wdumper;

import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.processors.FilteredRdfSerializer;
import io.github.bennofs.wdumper.processors.ProgressReporter;
import io.github.bennofs.wdumper.spec.DumpSpec;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.wikidata.wdtk.datamodel.implementation.PropertyIdValueImpl;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentDumpProcessor;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import org.wikidata.wdtk.dumpfiles.EntityTimerProcessor;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import org.wikidata.wdtk.rdf.PropertyRegister;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.wikidata.wdtk.datamodel.helpers.Datamodel.SITE_WIKIDATA;

public class DumpRunner {
    private final int id;
    private final DumpProcessingController controller;
    private final MwDumpFile dumpFile;
    private final PropertyRegister propertyRegister;

    private final List<EntityDocumentDumpProcessor> processors;
    private final List<OutputStream> outputStreams;

    private final Path outputDirectory;

    private DumpRunner(final int id, final MwDumpFile dumpFile, DumpProcessingController controller, PropertyRegister propertyRegister, Path outputDirectory) {
        Objects.requireNonNull(dumpFile);
        Objects.requireNonNull(controller);
        Objects.requireNonNull(propertyRegister);

        this.id = id;
        this.dumpFile = dumpFile;
        this.controller = controller;
        this.propertyRegister = propertyRegister;

        this.processors = new ArrayList<>();
        this.outputStreams = new ArrayList<>();

        this.outputDirectory = outputDirectory;
    }

    static public DumpRunner create(final int id, final MwDumpFile dumpFile, Path outputDirectory) {
        final DumpProcessingController controller = new DumpProcessingController("wikidatawiki");
        final PropertyRegister propertyRegister = wdPropertyRegisterFromSparql();

        return new DumpRunner(id, dumpFile, controller, propertyRegister, outputDirectory);
    }

    public void addDumpTask(int id, DumpSpec spec, DumpStatusHandler statusHandler) throws IOException {
        final OutputStream output = openGzipOutput(this.outputDirectory.resolve(id + ".nt.gz"));

        FilteredRdfSerializer serializer = new FilteredRdfSerializer(spec, output, controller.getSitesInformation(), propertyRegister, statusHandler);
        this.processors.add(serializer);
    }

    public void run(RunnerStatusHandler runnerStatusHandler) {
        this.processors.add(new ProgressReporter(60, runnerStatusHandler));

        for (EntityDocumentDumpProcessor processor : processors) {
            processor.open();
            controller.registerEntityDocumentProcessor(processor, null, true);
        }

        runnerStatusHandler.start();
        controller.processDump(this.dumpFile);
        runnerStatusHandler.done();

        for (EntityDocumentDumpProcessor processor : processors) {
            processor.close();
        }
    }

    public int getId() {
        return id;
    }

    private static OutputStream openGzipOutput(Path outputPath) throws IOException {
        final OutputStream fileStream = Files.newOutputStream(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        final OutputStream bufferedStream = new BufferedOutputStream(fileStream, 10 * 1024 * 1024);

        final GzipParameters gzipParameters = new GzipParameters();
        gzipParameters.setCompressionLevel(1);
        final OutputStream compressStream = new GzipCompressorOutputStream(bufferedStream, gzipParameters);
        return asynchronousOutputStream(compressStream);
    }

    private static PropertyRegister wdPropertyRegisterFromSparql() {
        final PropertyRegister propertyRegister = PropertyRegister.getWikidataPropertyRegister();

        // deleted properties, but still available in dumps
        propertyRegister.setPropertyType(new PropertyIdValueImpl("P6270", SITE_WIKIDATA), "http://wikiba.se/ontology#ExternalId");

        final SPARQLRepository repository = new SPARQLRepository("https://query.wikidata.org/sparql");
        repository.initialize();
        final RepositoryConnection connection = repository.getConnection();

        var query = connection.prepareTupleQuery("SELECT ?prop ?type WHERE { ?prop wikibase:propertyType ?type }");
        try (final var result = query.evaluate()) {
            while (result.hasNext()) {
                final BindingSet solution = result.next();
                final IRI property = (IRI)solution.getValue("prop");
                final IRI propType = (IRI)solution.getValue("type");
                final PropertyIdValue propId = new PropertyIdValueImpl(property.getLocalName(), SITE_WIKIDATA);
                propertyRegister.setPropertyType(propId, propType.toString());

            }
            return propertyRegister;
        } finally {
            repository.shutDown();
        }
    }

    /**
     * Creates a separate thread for writing into the given output stream and
     * returns a pipe output stream that can be used to pass data to this
     * thread.
     * <p>
     * This code is inspired by
     * http://stackoverflow.com/questions/12532073/gzipoutputstream
     * -that-does-its-compression-in-a-separate-thread
     *
     * @param outputStream
     *            the stream to write to in the thread
     * @return a new stream that data should be written to
     * @throws IOException
     *             if the pipes could not be created for some reason
     */
    public static OutputStream asynchronousOutputStream(
            final OutputStream outputStream) throws IOException {
        final int SIZE = 1024 * 1024 * 10;
        final PipedOutputStream pos = new PipedOutputStream();
        final PipedInputStream pis = new PipedInputStream(pos, SIZE);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] bytes = new byte[SIZE];
                    for (int len; (len = pis.read(bytes)) > 0;) {
                        outputStream.write(bytes, 0, len);
                    }
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                } finally {
                    close(pis);
                    close(outputStream);
                }
            }
        }, "async-output-stream").start();
        return pos;
    }

    /**
     * Closes a Closeable and swallows any exceptions that might occur in the
     * process.
     *
     * @param closeable
     */
    static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
            }
        }
    }
}
