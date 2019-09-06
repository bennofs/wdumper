package io.github.bennofs.wdumper.processors;

import com.google.common.collect.Sets;
import io.github.bennofs.wdumper.CanonicalValueFactory;
import io.github.bennofs.wdumper.diffing.ChildDiffer;
import io.github.bennofs.wdumper.diffing.Differ;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.spec.EntityFilter;
import io.github.bennofs.wdumper.spec.StatementFilter;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.rio.helpers.ParseErrorLogger;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.rdf.PropertyRegister;
import org.wikidata.wdtk.rdf.Vocabulary;

import java.io.*;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DiffingProcessor implements EntityDocumentDumpProcessor {
    private final static IRI Dataset;
    static {
        ValueFactory factory = SimpleValueFactory.getInstance();
        Dataset = factory.createIRI("http://schema.org/Dataset");
    }

    private final ValueFactory factory;

    private final BufferedReader dumpReader;
    private String dumpLastStmt;

    private final FilteredRdfSerializer serializer;
    private final ByteArrayOutputStream serializerOutput;

    private final Set<IRI> predicatesDumpOnly;
    private final Set<IRI> predicatesSerializedOnly;
    private final Set<IRI> predicatesSometimesMissing;

    public static DumpSpec getSpec() {
        return new DumpSpec(
                new EntityFilter[]{},
                Set.of(new StatementFilter(null, true, true, true, true)),
                null,
                true,
                true,
                true,
                false,
                true,
                true
        );
    }

    private static ParserConfig getParserConfig() {
        final ParserConfig config = new ParserConfig();
        config.set(BasicParserSettings.VERIFY_URI_SYNTAX, false);
        return config;
    }


    private static boolean isDatasetLine(final String line) {
        String[] parts = line.split(" ");
        if (parts.length < 3) return false;
        return parts[1].equals("<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>") && parts[2].equals("<http://schema.org/Dataset>");
    }

    private static boolean isEntitySameAs(final String line) {
        String[] parts = line.split(" ");
        return parts.length > 2 && parts[0].startsWith("<http://www.wikidata.org/entity/") && parts[1].equals("<http://www.w3.org/2002/07/owl#sameAs>");
    }

    private static String makeStatement(String line) {
        return line + "\n";
    }

    public DiffingProcessor(InputStream rdfStream, Sites sites, PropertyRegister propertyRegister) throws IOException {
        this.dumpReader = new BufferedReader(new InputStreamReader(rdfStream));
        this.serializerOutput = new ByteArrayOutputStream();
        this.serializer = new FilteredRdfSerializer(getSpec(), serializerOutput, sites, propertyRegister, new DumpStatusHandler() {
            @Override
            public void reportError(ErrorLevel level, String message) {

            }
        });
        int skip = 2;
        while (skip > 0) {
            dumpLastStmt = dumpReader.readLine();
            if (dumpLastStmt == null) break;
            if (isDatasetLine(dumpLastStmt)) skip -= 1;
        }
        this.factory = SimpleValueFactory.getInstance();
        this.predicatesDumpOnly = new HashSet<>();
        this.predicatesSerializedOnly = new HashSet<>();
        this.predicatesSometimesMissing = new HashSet<>();
    }

    @Override
    public void open() {
        this.serializer.open();
    }

    @Override
    public void close() {
        this.serializer.close();
        if (this.dumpLastStmt != null) {
            System.out.println("leftover statement!");
            System.out.println(this.dumpLastStmt);
            throw new RuntimeException("leftover statement! " + this.dumpLastStmt);
        }
    }

    private Model readDumpDoc() throws IOException {
        if (dumpLastStmt == null) return null;

        StringBuffer buffer = new StringBuffer();
        buffer.append(makeStatement(dumpLastStmt));
        boolean skip = false;
        while (true) {
            dumpLastStmt = dumpReader.readLine();
            if (dumpLastStmt == null) {
                break;
            }
            if (isDatasetLine(dumpLastStmt)) {
                if (skip) {
                    skip = false;
                } else {
                    break;
                }
            }
            if (isEntitySameAs(dumpLastStmt)) {
                buffer.setLength(0);
                skip = true;
                continue;
            }
            if (!skip) {
                buffer.append(makeStatement(dumpLastStmt));
            }
        }
        try {
            return Rio.parse(new StringReader(buffer.toString()), "http://localhost/dump/", RDFFormat.NTRIPLES, getParserConfig(), CanonicalValueFactory.getInstance(), new ParseErrorLogger());
        } catch(RDFParseException e) {
            System.err.println(buffer.toString());
            throw e;
        }
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {
        System.out.println("process item " + itemDocument.getEntityId().getId());

        // process the JSON document
        serializerOutput.reset();
        this.serializer.processItemDocument(itemDocument);
        this.serializer.flush();
        try {
            // read the next document from the dump stream
            final Model dumpModel = readDumpDoc();
            if (dumpModel == null) {
                throw new RuntimeException("no model in dump found for item " + itemDocument);
            }

            final Model serializerModel = Rio.parse(
                    new ByteArrayInputStream(serializerOutput.toByteArray()),
                    "http://localhost/dump/", RDFFormat.NTRIPLES, getParserConfig(), CanonicalValueFactory.getInstance(), new ParseErrorLogger());
            Optional<Statement> typeStmt = dumpModel
                    .filter(null, RDF.TYPE, factory.createIRI(Vocabulary.WB_ITEM))
                    .stream().findFirst();
            if (!typeStmt.isPresent()) {
                System.err.println(dumpModel);
                throw new RuntimeException("missing item type " + itemDocument);
            }
            if (!typeStmt.get().getSubject().toString().equals(itemDocument.getEntityId().getIri())) {
                throw new RuntimeException("desync! " + typeStmt.get().toString() + "\n\n" + itemDocument);
            }

            diffModel(dumpModel, serializerModel);
        } catch(RDFParseException e) {
            try {
                System.err.write(serializerOutput.toByteArray());
                throw e;
            } catch(IOException ignored) {
            }
        } catch(IOException e) {
            throw new RuntimeException("io error: " + e);
        }
    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
        System.out.println("process property " + propertyDocument.getEntityId().getId());
        // process the JSON document
        serializerOutput.reset();
        this.serializer.processPropertyDocument(propertyDocument);
        this.serializer.flush();
        try {
            // read the next document from the dump stream
            final Model dumpModel = readDumpDoc();
            if (dumpModel == null) {
                throw new RuntimeException("no model in dump found for property " + propertyDocument);
            }

            final Model serializerModel = Rio.parse(
                    new ByteArrayInputStream(serializerOutput.toByteArray()),
                    "http://localhost/dump/", RDFFormat.NTRIPLES);
        } catch (IOException e) {
            throw new RuntimeException("io error: " + e);
        }
    }

    @Override
    public void processLexemeDocument(LexemeDocument lexemeDocument) {
        final Model dumpModel;
        try {
            dumpModel = readDumpDoc();
        } catch (IOException e) {
            throw new RuntimeException("io error: " + e);
        }
        if (dumpModel == null) {
            throw new RuntimeException("no model in dump found for lexeme " + lexemeDocument);
        }
        System.out.println("skip lexeme");
    }

    @Override
    public void processMediaInfoDocument(MediaInfoDocument mediaInfoDocument) {
        final Model dumpModel;
        try {
            dumpModel = readDumpDoc();
        } catch (IOException e) {
            throw new RuntimeException("io error: " + e);
        }
        if (dumpModel == null) {
            throw new RuntimeException("no model in dump found for media info " + mediaInfoDocument);
        }
        System.out.println("skip media info");
    }
    private void diffModel(Model dumpModel, Model serializerModel) {
        final Differ differ = new Differ(dumpModel, serializerModel);

        differ.unifyBNodes();

        final List<ChildDiffer> statements = differ.extractSubjects("http://www.wikidata.org/entity/statement/");

        // compare statements
        for (ChildDiffer statement : statements) {
            final Model a = statement.getA();
            final Model b = statement.getB();
            if (a.size() == 0 || b.size() == 0) {
                System.out.println("!!! unmatched statement");
                System.out.println("dump " + a);
                System.out.println("serialized " + b);
            }
            statement.pullObjects("http://www.wikidata.org/reference/");
            statement.pullObjects("http://www.wikidata.org/value/");


        }

        // TODO
        final ChildDiffer owl = differ.extractObjects("http://www.w3.org/2002/07/owl#");

        // ignore entity data (not implemented)
        differ.extractSubjects("https://www.wikidata.org/wiki/Special:EntityData/");

        // compare remaining triples
        for (IRI predicate : differ.eliminatePredicatesOnlyA()) {
            if (predicatesDumpOnly.contains(predicate)) continue;
            predicatesDumpOnly.add(predicate);
            System.out.println("new predicate only in dump " + predicate);
        }

        for (IRI predicate : differ.eliminatePredicatesOnlyB()) {
            if (predicatesSerializedOnly.contains(predicate)) continue;
            predicatesSerializedOnly.add(predicate);
            System.out.println("new predicate only in serialized " + predicate);
        }

        differ.eliminateCommon();
        for (IRI predicate : Sets.union(differ.getA().predicates(), differ.getB().predicates())) {
            System.out.println("!!! predicate " + predicate);
            Model a = differ.getA().filter(null, predicate, null);
            Model b = differ.getB().filter(null, predicate, null);
            System.out.println("in dump");
            for (Statement stmt : a) {
                Rio.write(stmt, System.out, RDFFormat.NTRIPLES);
            }
            System.out.println("---");
            for (Statement stmt : b) {
                Rio.write(stmt, System.out, RDFFormat.NTRIPLES);
            }
            System.out.println("in serialized");
        }
    }
}
