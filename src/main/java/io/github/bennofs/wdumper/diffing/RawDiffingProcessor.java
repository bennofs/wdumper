package io.github.bennofs.wdumper.diffing;

import com.eaio.stringsearch.BoyerMooreHorspool;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.processors.FilteredRdfSerializer;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.spec.EntityFilter;
import io.github.bennofs.wdumper.spec.StatementFilter;
import org.eclipse.rdf4j.common.io.ByteArrayUtil;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.rdf.PropertyRegister;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class DiffTask {
    final public String entityId;
    final public byte[] serDoc;

    public DiffTask(String entityId, byte[] serDoc) {
        this.entityId = entityId;
        this.serDoc = serDoc;
    }
}

public class RawDiffingProcessor implements EntityDocumentDumpProcessor {
    private final InputStream dumpStream;
    private byte[] buffer;
    private int eob;

    private final FilteredRdfSerializer serializer;
    private final ByteArrayOutputStream serializerStream;
    private final Thread worker;
    private final BlockingQueue<DiffTask> taskQueue;

    private final ArrayList<ParsedDocument> dumpParsedQueue;
    private int countDumpSkipped = 0;
    private int countSerSkipped = 0;
    private int countRecentSkipped = 0;
    private int countDumpSameAs = 0;

    private final MatchMemorizer memo = new MatchMemorizer();

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

    public RawDiffingProcessor(InputStream rdfStream, Sites sites, PropertyRegister propertyRegister) throws IOException {
        this.dumpStream = rdfStream;
        this.buffer = new byte[0x1000000];
        this.eob = dumpStream.read(buffer);

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
                    e.printStackTrace();
                    System.exit(4);
                }
            }
        }, "diff-worker");
        this.dumpParsedQueue = new ArrayList<>();
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

    private final static int REORDER_BUFFER_SIZE = 10;

    private void doDiff(DiffTask task) {
        while (this.dumpParsedQueue.size() < REORDER_BUFFER_SIZE) {
            final byte[] doc = nextDumpDoc();
            if (doc == null) break;

            final ParsedDocument p = new ParsedDocument();
            if (!ParsedDocument.parse(doc, p)) {
                this.countDumpSameAs += 1;
                continue;
            }
            this.dumpParsedQueue.add(p);
        }

        if (this.dumpParsedQueue.size() == 0) {
            throw new RuntimeException("unexpected EOF");
        }

        final ParsedDocument parsedSer = new ParsedDocument(task.entityId);
        ParsedDocument.parse(task.serDoc, parsedSer);

        for (int i = 0; i < this.dumpParsedQueue.size(); ++i) {
            final ParsedDocument parsedDump = this.dumpParsedQueue.get(i);
            /*System.out.println("dump " + parsedDump.summarize());
            System.out.println("ser " + parsedSer.summarize());*/
            if (parsedDump.getId().equals(task.entityId)) {
                final DiffWikidataRDF diff = new DiffWikidataRDF(task.entityId, parsedDump, parsedSer, memo);
                diff.compute();
                this.countRecentSkipped = 0;
                this.dumpParsedQueue.remove(i);
                if (i > REORDER_BUFFER_SIZE / 2) {
                    for (int x = i - REORDER_BUFFER_SIZE / 2; x >= 0; --x) {
                        this.dumpParsedQueue.remove(0);
                        this.countDumpSkipped += 1;
                    }
                }
                return;
            }
        }

        if (countRecentSkipped > REORDER_BUFFER_SIZE) {
            System.out.flush();
            System.err.flush();
            throw new DesyncException("too many desyncs");
        }

        System.out.println("MISSING " + task.entityId);
        this.countRecentSkipped += 1;
        this.countSerSkipped += 1;
    }

    private void diff(DiffTask task) {
        try {
            this.taskQueue.put(task);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {
        this.serializerStream.reset();
        this.serializer.processItemDocument(itemDocument);
        this.serializer.flush();

        diff(new DiffTask(itemDocument.getEntityId().getId(), this.serializerStream.toByteArray()));
    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
        System.out.println("PROCESS property " + propertyDocument.getEntityId().getId());
        this.serializerStream.reset();
        this.serializer.processPropertyDocument(propertyDocument);
        this.serializer.flush();

        diff(new DiffTask(propertyDocument.getEntityId().getId(), this.serializerStream.toByteArray()));
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
