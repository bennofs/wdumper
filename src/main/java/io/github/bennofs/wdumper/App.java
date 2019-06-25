package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.luben.zstd.ZstdInputStream;
import io.github.bennofs.wdumper.spec.DumpSpec;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.implementation.PropertyIdValueImpl;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.Sites;
import org.wikidata.wdtk.dumpfiles.DumpProcessingController;
import org.wikidata.wdtk.dumpfiles.EntityTimerProcessor;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import org.wikidata.wdtk.dumpfiles.MwLocalDumpFile;
import org.wikidata.wdtk.rdf.PropertyRegister;
import picocli.CommandLine;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.wikidata.wdtk.datamodel.helpers.Datamodel.SITE_WIKIDATA;

public class App implements Runnable {
    @CommandLine.Parameters(paramLabel = "SPEC", arity = "1", index = "0", description = "JSON spec describing the properties of the dump")
    private Path dumpSpecPath;

    @CommandLine.Parameters(paramLabel = "DUMP", arity = "1", index = "1", description = "JSON dump from wikidata to process")
    private Path dumpFilePath;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean usageHelpRequested;

    private void processDump(DumpSpec spec, MwDumpFile dump) throws IOException {
        final DumpProcessingController dumpProcessingController = new DumpProcessingController("wikidatawiki");
        final Sites sites = dumpProcessingController.getSitesInformation();
        final PropertyRegister propertyRegister = wdPropertyRegisterFromSparql();

        final EntityTimerProcessor timer = new EntityTimerProcessor(0);
        final FilteredRdfSerializer serializer = new FilteredRdfSerializer(spec, System.out, sites, propertyRegister);
        dumpProcessingController.registerEntityDocumentProcessor(timer, null, true);
        dumpProcessingController.registerEntityDocumentProcessor(serializer, null, true);
        timer.open();
        serializer.open();
        dumpProcessingController.processDump(dump);
        serializer.close();
        timer.close();
    }

    public void run() {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.setInjectableValues(new InjectableValues.Std().addValue("siteIri", Datamodel.SITE_WIKIDATA));
            final DumpSpec spec = mapper.readValue(this.dumpSpecPath.toFile(), DumpSpec.class);

            final MwDumpFile dump = new ZstdDumpFile(this.dumpFilePath.toString());

            processDump(spec, dump);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }

    private static PropertyRegister wdPropertyRegisterFromSparql() {
        final PropertyRegister propertyRegister = PropertyRegister.getWikidataPropertyRegister();

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

    public static void main(String[] args) throws IOException {
        final App app = new App();
        new CommandLine(app).execute(args);
    }
}

class ZstdDumpFile extends MwLocalDumpFile {
    ZstdDumpFile(String filepath) {
        super(filepath);
    }

    @Override
    public InputStream getDumpFileStream() throws IOException {
        if (!this.getPath().toString().contains("zstd")) {
            return super.getDumpFileStream();
        }
        return new ZstdInputStream(new BufferedInputStream(Files.newInputStream(this.getPath(), StandardOpenOption.READ)));
    }
}
