package io.github.bennofs.wdumper.diffing;

import com.eaio.stringsearch.BoyerMooreHorspool;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.base.MoreObjects;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.processors.FilteredRdfSerializer;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.spec.EntityFilter;
import io.github.bennofs.wdumper.spec.StatementFilter;
import org.eclipse.rdf4j.common.io.ByteArrayUtil;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.helpers.DatamodelMapper;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.rdf.PropertyRegister;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class DiffTask {
    final public String entityId;
    final public byte[] serDoc;
    final EntityDocument doc;

    public DiffTask(String entityId, byte[] serDoc, EntityDocument doc) {
        this.entityId = entityId;
        this.serDoc = serDoc;
        this.doc = doc;
    }
}

class Command {
    public final JsonNode node;
    public final String id;
    public final String type;

    public Command(JsonNode node, String id, String type) {
        this.node = node;
        this.id = id;
        this.type = type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("type", type)
                .toString();
    }
}


public class RawDiffingProcessor implements EntityDocumentDumpProcessor {
    private final InputStream dumpStream;
    private byte[] buffer;
    private int eob;

    private final Iterator<JsonNode> commands;
    private Command nextCommand;
    private final ObjectMapper mapper;

    private final FilteredRdfSerializer serializer;
    private final ByteArrayOutputStream serializerStream;
    private final Thread worker;
    private final BlockingQueue<DiffTask> taskQueue;
    private final Queue<Command> dumpCommands = new ConcurrentLinkedDeque<>();
    private final MatchMemorizer memo = new MatchMemorizer();
    private final BiConsumer<byte[], Diff> diffHandler;

    static final byte[] ENTITY_DATA_UTF8;
    static final Object ENTITY_DATA_UTF8_PROCESSED;
    static final BoyerMooreHorspool matcher = new BoyerMooreHorspool();
    static {
        ENTITY_DATA_UTF8 = "\n<https://www.wikidata.org/wiki/Special:EntityData/".getBytes(StandardCharsets.UTF_8);
        ENTITY_DATA_UTF8_PROCESSED = matcher.processBytes(ENTITY_DATA_UTF8);
    }

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

    public RawDiffingProcessor(InputStream rdfStream, InputStream commandStream, Sites sites, PropertyRegister propertyRegister, BiConsumer<byte[], Diff> diffHandler) throws IOException {
        this.dumpStream = rdfStream;
        this.buffer = new byte[0x1000000];
        this.eob = dumpStream.read(buffer);

        this.mapper = new DatamodelMapper(Datamodel.SITE_WIKIDATA);
        this.commands = this.mapper.readerFor(JsonNode.class).readValues(commandStream);
        readNextCommand();

        int start = matcher.searchBytes(this.buffer, 0, this.eob, ENTITY_DATA_UTF8, ENTITY_DATA_UTF8_PROCESSED);
        assert (start != -1);
        System.arraycopy(this.buffer, start, this.buffer, 0, this.eob - start);
        this.eob -= start;
        this.serializerStream = new ByteArrayOutputStream();
        this.serializer = new FilteredRdfSerializer(getSpec(), serializerStream, sites, propertyRegister, new DumpStatusHandler() {
            @Override
            public void reportError(ErrorLevel level, String message) {

            }
        });

        this.taskQueue = new ArrayBlockingQueue<>(100);

        this.worker = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    final DiffTask task = this.taskQueue.take();
                    doDiff(task);
                } catch(InterruptedException e) {
                    break;
                } catch(Exception e) {
                    System.err.println("ERROR during diff processing " + e.toString());
                    e.printStackTrace();
                }
            }
        }, "diff-worker");

        this.diffHandler = diffHandler;
    }

    @Override
    public void open() {
        this.serializer.open();
        this.worker.start();
    }

    @Override
    public void close() {
        this.serializer.close();
        this.worker.interrupt();
        try {
            this.worker.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Command readCommand() {
        if (!this.commands.hasNext()) return null;
        final JsonNode json = this.commands.next();
        if (json == null) return null;
        return new Command(json, json.get("id").asText(), json.get("type").asText());
    }

    private void readNextCommand() {
        while (true) {
            this.nextCommand = readCommand();
            if (this.nextCommand == null) {
                System.err.println("END OF COMMANDS");
                return;
            }
	    System.err.println("NEXT: " + nextCommand.id);
            if (this.nextCommand.type.equals("skip_json") || this.nextCommand.type.equals("item") || this.nextCommand.type.equals("property")) {
                return;
            }
            this.dumpCommands.add(this.nextCommand);
        }
    }

    private int refillBuffer() {
        try {
            if (this.eob == this.buffer.length) {
                if (buffer.length > 0x4000000) {
                    System.err.println("EXCEED max buffer size");
                    System.exit(2);
                }
                System.err.println("RESIZE buffer, current size: " + String.format("%x", this.buffer.length));
                final byte[] oldbuf = this.buffer;
                this.buffer = new byte[this.buffer.length * 2];
                System.arraycopy(oldbuf, 0, this.buffer, 0, oldbuf.length);
            }
            int r = this.dumpStream.read(this.buffer, this.eob, this.buffer.length - this.eob);
            if (r != -1) this.eob += r;
            return r;
        } catch(IOException|IndexOutOfBoundsException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return -1;
    }

    private byte[] nextDumpDoc() {
        if (this.buffer.length == 0) return null;

        int entityEnd = ByteArrayUtil.find(this.buffer, 1, this.eob, (byte)0x20);
        while (entityEnd == -1) {
            if (refillBuffer() == -1) {
                return null;
            };
            entityEnd = ByteArrayUtil.find(this.buffer, 1, this.eob, (byte)0x20);
        }
        int start = entityEnd;
        while (true) {
            int idx = matcher.searchBytes(this.buffer, start, this.eob, ENTITY_DATA_UTF8, ENTITY_DATA_UTF8_PROCESSED);
            if (idx == -1 || idx + entityEnd > this.eob) {
                if (refillBuffer() == -1) {
                    final byte[] out = Arrays.copyOfRange(this.buffer, 1, this.buffer.length);
                    this.buffer = new byte[0];
                    return out;
                }
                if (idx == -1) continue;
            }
            if (Arrays.equals(this.buffer, idx, idx + entityEnd, this.buffer, 0, entityEnd)) {
                start = idx + entityEnd;
                continue;
            }

            final byte[] out = Arrays.copyOfRange(this.buffer, 1, idx + 1);
            this.eob -= idx;
            System.arraycopy(this.buffer, idx, this.buffer, 0, this.eob);
            return out;
        }
    }

    private void doDiff(DiffTask task) throws JsonProcessingException {
        final ParsedDocument parsedDump;
        while (true) {
            final byte[] doc = nextDumpDoc();
            final Command nextCommand = dumpCommands.peek();

            if (doc == null) {
                throw new RuntimeException("unexpected EOF");
            }

            final ParsedDocument p = new ParsedDocument();
            final boolean parseOk = ParsedDocument.parse(doc, p);
            if (nextCommand != null && nextCommand.type.equals("skip_nt") && p.getId().equals(nextCommand.id)) {
                dumpCommands.remove();
                continue;
            }
            if (nextCommand != null && nextCommand.type.equals("need_update") && p.getId().equals(nextCommand.id)) {
                parsedDump = p;
                break;
            }

            if (!parseOk) {
                continue;
            }
            parsedDump = p;
            break;
        }

        final ParsedDocument parsedSer = new ParsedDocument(task.entityId);
        ParsedDocument.parse(task.serDoc, parsedSer);

        if (parsedDump.getId().equals(task.entityId)) {
            final Command nextCommand = dumpCommands.peek();
            if (nextCommand != null && nextCommand.type.equals("need_update") && parsedDump.getId().equals(nextCommand.id)) {
                dumpCommands.remove();
                System.err.println("SKIP due to missing update " + parsedDump.getId() + ":" + parsedSer.getId());
                return;
            }

            final Diff diff = new DiffWikidataRDF(task.entityId, parsedDump, parsedSer, memo).compute();
            if (!diff.differences.isEmpty()) {
                diffHandler.accept(mapper.writeValueAsBytes(task.doc), diff);
            }
        } else {
            for (Command command : dumpCommands) {
                System.err.println("COMMAND QUEUE " + command.toString());
            }
            throw new RuntimeException("Out of sync! " + parsedDump.getId() + ":" + parsedSer.getId());
        }
    }

    private void diff(DiffTask task) {
        try {
            this.taskQueue.put(task);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private <T extends EntityDocument> T checkCommand(T doc) {
        if (nextCommand == null) return doc;
        if (nextCommand.type.equals("skip_json") && nextCommand.id.equals(doc.getEntityId().getId())) {
            readNextCommand();
            return null;
        }
        if ((nextCommand.type.equals("item") || nextCommand.type.equals("property")) && nextCommand.id.equals(doc.getEntityId().getId())) {
            System.err.println("UPDATE JSON " + doc.getEntityId().getId());
            final T value = mapper.convertValue(nextCommand.node, (Class<T>)doc.getClass());
            readNextCommand();
            return value;
        }

        return doc;
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {
        try {
            System.out.println(this.mapper.writeValueAsString(itemDocument));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        this.serializerStream.reset();
        itemDocument = this.checkCommand(itemDocument);
        if (itemDocument == null) return;
        this.serializer.processItemDocument(itemDocument);
        this.serializer.flush();

        diff(new DiffTask(itemDocument.getEntityId().getId(), this.serializerStream.toByteArray(), itemDocument));
    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
        System.out.println("PROCESS property " + propertyDocument.getEntityId().getId());
        this.serializerStream.reset();
        propertyDocument = this.checkCommand(propertyDocument);
        if (propertyDocument == null) return;
        this.serializer.processPropertyDocument(propertyDocument);
        this.serializer.flush();

        diff(new DiffTask(propertyDocument.getEntityId().getId(), this.serializerStream.toByteArray(), propertyDocument));
    }

    @Override
    public void processLexemeDocument(LexemeDocument lexemeDocument) {
        System.err.println("SKIP lexeme");
    }

    @Override
    public void processMediaInfoDocument(MediaInfoDocument mediaInfoDocument) {
        System.err.println("SKIP mediainfo");
    }
}
