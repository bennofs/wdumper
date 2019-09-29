package io.github.bennofs.wdumper.diffing;

import com.google.common.collect.Sets;

import java.util.*;

public class DiffWikidataRDF {
    private Diff diff;
    private final String entityId;
    private final ParsedDocument pDump;
    private final ParsedDocument pSer;

    public DiffWikidataRDF(String entityId, ParsedDocument pDump, ParsedDocument pSer) {
        this.entityId = entityId;
        this.pDump = pDump;
        this.pSer = pSer;
    }

    public void reportDifference(String tag, Set<Triple> inDump, Set<Triple> inSer) {
        System.out.println("DIFF " + this.entityId + " " + tag);
        if (this.diff == null) {
            this.diff = new Diff(entityId, pDump, pSer);
        }
        for (Triple t : inDump) {
            System.out.println(t.toString());
        }
        System.out.println("---");
        for (Triple t : inSer) {
            System.out.println(t.toString());
        }
        this.diff.differences.add(new Diff.Difference(tag, inDump, inSer));
    }

    public String diffName(ParsedDocument.BlankNode dump, ParsedDocument.BlankNode ser) {
        if (dump.subject.equals(ser.subject)) {
            return Utils.bufferString(ser.subject);
        }

        return Utils.bufferString(dump.subject) + "-" + Utils.bufferString(ser.subject);
    }

    public void diffOwnTriples(String prefix, ParsedDocument.BlankNode dump, ParsedDocument.BlankNode ser) {
        if (dump.isEmpty() || ser.isEmpty()) return;

        dump.reset();
        ser.reset();
        dump.matchOther(ser);
        if (dump.other.size() + ser.other.size() != 0) {
            this.reportDifference(prefix + diffName(dump, ser)+ "/" + "other", dump.other, ser.other);
        }
    }

    public <T extends ParsedDocument.BlankNode> void diffNodeKind(String prefix, Collection<T> dump, Collection<T> ser) {
        // matched nodes & unmatched in dump
        for (ParsedDocument.BlankNode node : dump) {
            if (pDump.isOrphan(node)) continue;
            if (node.matchedNode == null) {
                reportDifference(prefix + "unmatched/dump/" + Utils.bufferString(node.subject),
                        Sets.union(node.other, node.links),
                        Collections.emptySet());
            } else {
                diffOwnTriples(prefix + "triples/", node, node.matchedNode);
            }
        }
        // report unmatched nodes in ser
        for (ParsedDocument.BlankNode node : ser) {
            if (node.matchedNode != null || pSer.isOrphan(node)) continue;
            reportDifference(prefix + "unmatched/serialized/" + Utils.bufferString(node.subject),
                    Collections.emptySet(),
                    Sets.union(node.other, node.links));
        }
    }

    public Diff compute() {
        this.pDump.matchNodes(this.pSer);

        diffNodeKind("/statement/", this.pDump.allStatements.values(), this.pSer.allStatements.values());
        diffNodeKind("/reference/", this.pDump.allReferences.values(), this.pSer.allReferences.values());
        diffNodeKind("/value/", this.pDump.allValueNodes.values(), this.pSer.allValueNodes.values());
        diffNodeKind("/bnode/", this.pDump.allBNodes.values(), this.pSer.allBNodes.values());
        diffOwnTriples("", this.pDump.getRoot(), this.pSer.getRoot());

        return this.diff;
    }
}
