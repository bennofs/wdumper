package io.github.bennofs.wdumper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.luben.zstd.ZstdInputStream;
import io.github.bennofs.wdumper.diffing.Diff;
import io.github.bennofs.wdumper.diffing.ParsedDocument;
import io.github.bennofs.wdumper.diffing.RawDiffingProcessor;
import io.github.bennofs.wdumper.diffing.Utils;
import io.github.bennofs.wdumper.ext.StreamDumpFile;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocumentDumpProcessor;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import org.wikidata.wdtk.dumpfiles.EntityTimerProcessor;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import org.wikidata.wdtk.rdf.PropertyRegister;
import picocli.CommandLine;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public class CliComparer implements Runnable {
    @CommandLine.Parameters(paramLabel = "DUMP", arity = "1", index = "0", description = "JSON dump from wikidata to process")
    private URI dumpFilePath;

    @CommandLine.Parameters(paramLabel = "RDF", arity = "1", index = "1", description = "RDF dump from wikidata to compare against")
    private URI rdfFilePath;

    @CommandLine.Parameters(paramLabel = "COMMANDS", arity = "1", index = "2", description = "File with commands for aligning the dumps")
    private URI commandFilePath;

    @CommandLine.Option(names = "-d", description="output directory for differences", defaultValue = "diff")
    private Path outputDir;

    final private ObjectMapper mapper = new ObjectMapper();

    private void handleDiff(byte[] jsonDoc, Diff d) {
        try {
            final Path diffDir = this.outputDir.resolve(d.entityId);
            Files.createDirectories(diffDir);
            Files.write(diffDir.resolve("dump.nt"), Utils.bufferBytes(d.docDump.getOrigRawDoc()));
            Files.write(diffDir.resolve("generated.nt"), Utils.bufferBytes(d.docSerialized.getOrigRawDoc()));
            Files.write(diffDir.resolve("entity.json"), jsonDoc);

            try (BufferedWriter memo = Files.newBufferedWriter(diffDir.resolve("memo.csv"))) {
                for (ParsedDocument.MemoMatch match : d.docDump.getMemoMatches()) {
                    memo.write(Utils.bufferString(match.from) + "," + Utils.bufferString(match.to) + "\n");
                }
            }

            int idx = 0;
            for (Diff.Difference difference : d.differences) {
                mapper.writeValue(diffDir.resolve(idx + ".diff.json").toFile(), difference);
                idx += 1;
            }
        } catch(IOException e) {
            System.err.println("ERROR cannot write diff " + e);
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            final MwDumpFile dumpFile = new StreamDumpFile("2019", getInputStream(dumpFilePath));
            Files.createDirectories(this.outputDir);

            final ObjectMapper mapper = new ObjectMapper();
            System.err.println("using spec:");
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            mapper.writeValue(System.err, RawDiffingProcessor.getSpec());
            System.err.println("");

            final DumpProcessingController controller = new DumpProcessingController("wikidatawiki");

            //ObjectInputStream read = new ObjectInputStream(new FileInputStream("/tmp/properties.bin"));
            final PropertyRegister propertyRegister = PropertyRegister.getWikidataPropertyRegister();
            propertyRegister.fetchUsingSPARQL(new URI("https://query.wikidata.org/sparql"));

            ObjectOutputStream save = new ObjectOutputStream(new FileOutputStream("/tmp/properties.bin"));
            save.writeObject(propertyRegister);

            final EntityDocumentDumpProcessor processor = new RawDiffingProcessor(getInputStream(rdfFilePath), getInputStream(commandFilePath), controller.getSitesInformation(), propertyRegister, this::handleDiff);
            final EntityDocumentDumpProcessor timer = new EntityTimerProcessor(0);
            controller.registerEntityDocumentProcessor(processor, null, true);
            controller.registerEntityDocumentProcessor(timer, null, true);
            processor.open();
            timer.open();
            System.out.println("processing");
            controller.processDump(dumpFile);
            processor.close();
            timer.close();
        } catch(Exception e) {
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
