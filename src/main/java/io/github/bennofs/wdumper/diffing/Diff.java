package io.github.bennofs.wdumper.diffing;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Diff {
    public static class Difference {
        public final String tag;
        public final Set<Triple> inDump;
        public final Set<Triple> inSerialized;

        @JsonCreator
        public Difference(
                @JsonProperty("tag")
                String tag,
                @JsonProperty("inDump")
                Set<Triple> inDump,
                @JsonProperty("inSerialized")
                Set<Triple> inSerialized) {
            this.tag = tag;
            this.inDump = ImmutableSet.copyOf(inDump);
            this.inSerialized = ImmutableSet.copyOf(inSerialized);
        }
    }

    public final String entityId;
    public final List<Difference> differences = new ArrayList<>();
    public final ParsedDocument docDump;
    public final ParsedDocument docSerialized;

    public Diff(String entityId, ParsedDocument docDump, ParsedDocument docSerialized) {
        this.entityId = entityId;
        this.docDump = docDump;
        this.docSerialized = docSerialized;
    }
}
