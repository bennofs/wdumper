package io.github.bennofs.wdumper;

import com.google.common.collect.ImmutableList;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.processors.FilteredRdfSerializer;
import io.github.bennofs.wdumper.processors.ProgressReporter;
import io.github.bennofs.wdumper.spec.DumpSpec;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentDumpProcessor;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import org.wikidata.wdtk.rdf.PropertyRegister;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class DumpRunner {
    private final int id;
    private final Config config;
    private final DumpProcessingController controller;
    private final MwDumpFile dumpFile;
    private final PropertyRegister propertyRegister;

    private final List<FilteredRdfSerializer> serializers;

    /**
     * For documentation see implementation at {@link io.github.bennofs.wdumper.Config}
     */
    public interface Config {
        Path dumpStorageDirectory();
        Duration runProgressInterval();
    }

    private DumpRunner(final int id, final Config config, final MwDumpFile dumpFile, DumpProcessingController controller, PropertyRegister propertyRegister) {
        Objects.requireNonNull(dumpFile);
        Objects.requireNonNull(controller);
        Objects.requireNonNull(propertyRegister);

        this.id = id;
        this.dumpFile = dumpFile;
        this.controller = controller;
        this.propertyRegister = propertyRegister;
        this.config = config;

        this.serializers = new ArrayList<>();
    }

    static public DumpRunner create(final int id, final Config config, final MwDumpFile dumpFile) {
        final DumpProcessingController controller = new DumpProcessingController("wikidatawiki");
        final PropertyRegister propertyRegister = PropertyRegister.getWikidataPropertyRegister();

        return new DumpRunner(id, config, dumpFile, controller, propertyRegister);
    }

    public static Path getOutputPath(Path outputDirectory, final int id) {
        return outputDirectory.resolve("wdump-" + id + ".nt.gz");
    }

    void addDumpTask(int id, DumpSpec spec, DumpStatusHandler statusHandler) throws IOException {
        final OutputStream output = openGzipOutput(getOutputPath(this.config.dumpStorageDirectory(), id));

        FilteredRdfSerializer serializer = new FilteredRdfSerializer(spec, id, output, controller.getSitesInformation(), propertyRegister, statusHandler);
        this.serializers.add(serializer);
    }

    public void run(RunnerStatusHandler runnerStatusHandler) {
        final EntityDocumentDumpProcessor progressProcessor = new ProgressReporter(config.runProgressInterval(), runnerStatusHandler);

        Stream.concat(serializers.stream(), Stream.of(progressProcessor)).forEach(processor -> {
            processor.open();
            controller.registerEntityDocumentProcessor(processor, null, true);
        });

        runnerStatusHandler.start();
        controller.processDump(this.dumpFile);
        Stream.concat(serializers.stream(), Stream.of(progressProcessor)).forEach(EntityDocumentDumpProcessor::close);
        runnerStatusHandler.done();
    }

    public List<FilteredRdfSerializer> getSerializers() {
        return ImmutableList.copyOf(this.serializers);
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

    /**
     * Helper class that joins a thread on a call to close, to ensure that the output stream has really been closed.
     */
    private static final class SyncCloseOutputStream extends FilterOutputStream {
        private final Thread worker;

        public SyncCloseOutputStream(OutputStream out, Thread worker) {
            super(out);
            this.worker = worker;
        }

        @Override
        public void close() throws IOException {
            super.close();
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
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
        final Thread worker = new Thread(() -> {
            try {
                byte[] bytes = new byte[SIZE];
                for (int len; (len = pis.read(bytes)) > 0; ) {
                    outputStream.write(bytes, 0, len);
                }
            } catch (IOException ioException) {
                ioException.printStackTrace();
            } finally {
                close(pis);
                close(outputStream);
            }
        }, "async-output-stream");
        worker.start();
        return new SyncCloseOutputStream(pos, worker);
    }

    /**
     * Closes a Closeable and swallows any exceptions that might occur in the
     * process
     *
     * @param closeable The object to close
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
