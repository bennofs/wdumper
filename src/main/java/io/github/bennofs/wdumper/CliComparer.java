package io.github.bennofs.wdumper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.luben.zstd.ZstdInputStream;
import io.github.bennofs.wdumper.ext.StreamDumpFile;
import io.github.bennofs.wdumper.ext.ZstdDumpFile;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.processors.DiffingProcessor;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.spec.EntityFilter;
import io.github.bennofs.wdumper.spec.StatementFilter;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentDumpProcessor;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import org.wikidata.wdtk.dumpfiles.EntityTimerProcessor;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import org.wikidata.wdtk.dumpfiles.MwLocalDumpFile;
import org.wikidata.wdtk.rdf.PropertyRegister;
import picocli.CommandLine;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class CliComparer implements Runnable {
    @CommandLine.Parameters(paramLabel = "DUMP", arity = "1", index = "0", description = "JSON dump from wikidata to process")
    private URI dumpFilePath;

    @CommandLine.Parameters(paramLabel = "RDF", arity = "1", index = "1", description = "RDF dump from wikidata to compare against")
    private URI rdfFilePath;

    @Override
    public void run() {
        try {
            final MwDumpFile dumpFile = new StreamDumpFile("2019", getInputStream(dumpFilePath));

            final ObjectMapper mapper = new ObjectMapper();
            System.err.println("using spec:");
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            mapper.writeValue(System.err, DiffingProcessor.getSpec());
            System.err.println("");

            final DumpProcessingController controller = new DumpProcessingController("wikidatawiki");
            final PropertyRegister propertyRegister = PropertyRegister.getWikidataPropertyRegister();
            propertyRegister.fetchUsingSPARQL(new URI("https://query.wikidata.org/sparql"));

            final EntityDocumentDumpProcessor processor = new DiffingProcessor(getInputStream(rdfFilePath), controller.getSitesInformation(), propertyRegister);
            final EntityDocumentDumpProcessor timer = new EntityTimerProcessor(0);
            controller.registerEntityDocumentProcessor(processor, null, true);
            controller.registerEntityDocumentProcessor(timer, null, true);
            processor.open();
            timer.open();
            System.out.println("processing");
            controller.processDump(dumpFile);
            processor.close();
            timer.close();
        } catch(IOException|URISyntaxException|RuntimeException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public InputStream getInputStream(URI uri) throws IOException {
        final InputStream stream;
        if (uri.getScheme() == null || uri.getScheme().equals("file")) {
            stream = new FileInputStream(uri.getPath());
        } else if (uri.getScheme().equals("tcp")) {
            final Socket socket = new Socket();
            socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()));
            stream = socket.getInputStream();
        } else if (uri.getScheme().equals("fd")) {
            stream = new FileInputStream("/proc/self/fd/" + uri.getHost());
        } else {
            throw new IllegalArgumentException("illegal URI scheme");
        }

        if (uri.getPath().contains(".zst") || uri.getQuery() != null && uri.getQuery().contains("zst")) {
            return new ZstdInputStream(stream);
        }

        if (uri.getPath().contains(".gz") || uri.getQuery() != null && uri.getQuery().contains("gz")) {
            return new GZIPInputStream(stream);
        }

        return stream;
    }

    public static void main(String[] args) {
        new CommandLine(new CliComparer()).execute(args);
    }
}
