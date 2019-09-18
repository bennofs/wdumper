package io.github.bennofs.wdumper.diffing;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class MatchMemorizer {
    private final Map<ByteBuffer, ByteBuffer> memorized = new HashMap<>();

    final void memorize(ParsedDocument.BlankNode a, ParsedDocument.BlankNode b) {
        memorized.put(a.subject, b.subject);
    }

    public void recall(ParsedDocument a, ParsedDocument b) {
        a.getAllNodes().forEach(n -> {
            n.matchedNode = b.findNode(memorized.get(n.subject));
            if (n.matchedNode != null) {
                n.matchedNode.matchedNode = n;
            }
        });
    }

    final boolean skip() {
        return false;
    }
}
