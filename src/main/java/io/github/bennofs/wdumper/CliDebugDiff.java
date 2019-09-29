package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.processors.FilteredRdfSerializer;
import org.apache.commons.io.output.NullOutputStream;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.helpers.DatamodelMapper;
import org.wikidata.wdtk.datamodel.implementation.EntityDocumentImpl;
import org.wikidata.wdtk.datamodel.interfaces.EntityDocument;
import org.wikidata.wdtk.datamodel.interfaces.ItemDocument;
import org.wikidata.wdtk.datamodel.interfaces.PropertyDocument;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import org.wikidata.wdtk.rdf.PropertyRegister;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import static io.github.bennofs.wdumper.diffing.RawDiffingProcessor.getSpec;

public class CliDebugDiff implements Runnable {
    @CommandLine.Parameters(paramLabel = "DIR", description="output directory for differences", arity = "1", index="0")
    private Path dir;

    @Override
    public void run() {
        final DumpProcessingController controller = new DumpProcessingController("wikidatawiki");

        final PropertyRegister propertyRegister = PropertyRegister.getWikidataPropertyRegister();
        propertyRegister.fetchUsingSPARQL(URI.create("https://query.wikidata.org/sparql"));

        try {
            final FilteredRdfSerializer serializer = new FilteredRdfSerializer(getSpec(), System.out, controller.getSitesInformation(), propertyRegister, new DumpStatusHandler() {
                @Override
                public void reportError(ErrorLevel level, String message, Exception cause) {
                    throw new RuntimeException(message, cause);
                }
            });
            serializer.open();

            final ObjectMapper mapper = new DatamodelMapper(Datamodel.SITE_WIKIDATA);
            final EntityDocument doc = mapper.readValue(dir.resolve("entity.json").toFile(), EntityDocumentImpl.class);
            if (doc instanceof PropertyDocument) {
                serializer.processPropertyDocument((PropertyDocument)doc);
            } else if (doc instanceof ItemDocument) {
                serializer.processItemDocument((ItemDocument)doc);
            }
            serializer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new CommandLine(new CliDebugDiff()).execute(args);
    }
}
