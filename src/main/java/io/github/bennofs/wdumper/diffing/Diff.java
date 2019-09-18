package io.github.bennofs.wdumper.diffing;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Diff {
    public static class Difference {
        public final String tag;
        public final Set<Triple> inDump;
        public final Set<Triple> inSerialized;

        public Difference(String tag, Set<Triple> inDump, Set<Triple> inSerialized) {
            this.tag = tag;
            this.inDump = inDump;
            this.inSerialized = inSerialized;
        }
    }

    public final String entityId;
    public final List<Difference> differences = new ArrayList<>();

    public Diff(String entityId) {
        this.entityId = entityId;
    }
}
