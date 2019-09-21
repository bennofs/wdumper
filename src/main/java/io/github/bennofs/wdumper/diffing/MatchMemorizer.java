package io.github.bennofs.wdumper.diffing;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public class MatchMemorizer {
    private static class LRUCache extends LinkedHashMap<ByteBuffer, ByteBuffer> {
        private final int maxSize;

        public LRUCache(int maxSize) {
            super(maxSize, 0.75F, true);
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<ByteBuffer, ByteBuffer> eldest) {
            return size() > maxSize;
        }
    }

    private final Map<ByteBuffer, ByteBuffer> valuesMemo = new LRUCache(100000);
    private final Map<ByteBuffer, ByteBuffer> referencesMemo = new LRUCache(100000);

    final void memorize(ParsedDocument.BlankNode a, ParsedDocument.BlankNode b) {
        byte[] prefix = readPrefix(a.subject);
        if (readPrefix(b.subject) != prefix) {
            a.subject.reset();
            b.subject.reset();
            throw new IllegalArgumentException("prefixes don't match!");
        }
        if (prefix != null) {
            byte[] key = new byte[a.subject.remaining()];
            a.subject.get(key);
            byte[] value = new byte[b.subject.remaining()];
            b.subject.get(value);
            getMapFor(prefix).put(ByteBuffer.wrap(key), ByteBuffer.wrap(value));
        }
        a.subject.reset();
        b.subject.reset();
    }

    public void recall(ParsedDocument a, ParsedDocument b) {
        a.getAllNodes().forEach(n -> {
            final ParsedDocument.BlankNode sameSubjectNode = b.findNode(n.subject);
            if (sameSubjectNode != null) {
                n.matchedNode = sameSubjectNode;
                sameSubjectNode.matchedNode = n;
                n.setStable(a);
                n.matchedNode.setStable(b);
                return;
            }

            final byte[] prefix = readPrefix(n.subject);
            if (prefix != null) {
                final ByteBuffer memo = getMapFor(prefix).get(n.subject.slice());
                if (memo != null) {
                    final ByteBuffer key = ByteBuffer.allocate(prefix.length + memo.remaining());
                    memo.mark();
                    key.put(prefix);
                    key.put(memo);
                    memo.reset();
                    key.flip();
                    n.matchedNode = b.findNode(key);
                    if (n.matchedNode != null) { ;
                        n.matchedNode.matchedNode = n;
                    }
                }
            }
            n.subject.reset();
        });
    }

    private byte[] readPrefix(ByteBuffer subject) {
        subject.mark();
        if (Utils.isPrefix(Utils.VALUE_PREFIX_UTF8, subject)) {
            subject.position(subject.position() + Utils.VALUE_PREFIX_UTF8.length);
            return Utils.VALUE_PREFIX_UTF8;
        }

        if (Utils.isPrefix(Utils.REFERENCE_PREFIX_UTF8, subject)) {
            subject.position(subject.position() + Utils.VALUE_PREFIX_UTF8.length);
            return Utils.REFERENCE_PREFIX_UTF8;
        }

        return null;
    }

    private Map<ByteBuffer, ByteBuffer> getMapFor(byte[] prefix) {
        if (prefix == Utils.VALUE_PREFIX_UTF8) return this.valuesMemo;
        if (prefix == Utils.REFERENCE_PREFIX_UTF8) return this.referencesMemo;
        throw new IllegalArgumentException("wrong prefix");
    }

    final boolean skip() {
        return false;
    }
}
