package io.github.bennofs.wdumper.diffing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.rdf4j.common.io.ByteArrayUtil;
import org.eclipse.rdf4j.rio.ntriples.NTriplesUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParsedDocument {
    public static class BlankNode {
        final ByteBuffer subject;
        public boolean orphan = false;
        boolean diffed = false;
        Set<Triple> links = new HashSet<>();
        final Set<Triple> other = new HashSet<>();
        final Set<Triple> origOther = new HashSet<>();
        BlankNode matchedNode = null;
        final Set<Pair<ByteBuffer, ByteBuffer>> stableReferrers = new HashSet<>();
        private boolean relativize;
        boolean stableId;

        public BlankNode(ByteBuffer subject, boolean relativize, boolean stableId) {
            this.subject = subject;
            this.relativize = relativize;
            this.stableId = stableId;
        }

        public void add(ParsedDocument context, Triple t) {
            if (relativize && t.subject.equals(this.subject)) {
                t = new Triple(Utils.SUBJECT_SELF, t.predicate, t.object);
            }
            other.add(t);
            origOther.add(t);
        }

        public boolean isEmpty() {
            return this.origOther.isEmpty();
        }

        public void matchOther(BlankNode b) {
            diffed = true;
            b.diffed = true;
            other.removeIf(b.other::remove);
        }

        Stream<BlankNode> getLinked() {
            return Stream.empty();
        }

        @Override
        public String toString() {
            return "Node(subject=" + Utils.bufferString(this.subject) +")";
        }

        public void reset() {
            if (!diffed) return;
            diffed = false;
            this.other.clear();
            this.other.addAll(origOther);
        }
    }

    public static class Node extends BlankNode {
        final Map<ByteBuffer, Set<BlankNode>> bnodes = new HashMap<>();

        public Node(ByteBuffer subject, boolean relativize, boolean stableId) {
            super(subject, relativize, stableId);
        }

        @Override
        public void add(ParsedDocument context, Triple t) {
            if (Utils.isPrefix(Utils.BNODE_PREFIX_UTF8, t.object)) {
                final BlankNode bnode = context.bNode(t.object);
                this.bnodes.computeIfAbsent(t.predicate, k -> new HashSet<>()).add(bnode);
                bnode.links.add(t);
                if (stableId) {
                    bnode.stableReferrers.add(Pair.of(this.subject, t.predicate));
                }
                return;
            }
            super.add(context, t);
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && this.bnodes.isEmpty();
        }

        public Stream<BlankNode> getBNodes() {
            return this.bnodes.values().stream().flatMap(Collection::stream);
        }

        @Override
        Stream<BlankNode> getLinked() {
            return Stream.concat(super.getLinked(), getBNodes());
        }
    }

    public static class NodeWithValues extends Node {
        final Map<ByteBuffer, Set<Node>> values = new HashMap<>();

        public NodeWithValues(ByteBuffer subject, boolean relativize, boolean stableId) {
            super(subject, relativize, stableId);
        }

        @Override
        public void add(ParsedDocument context, Triple t) {
            if (Utils.isPrefix(Utils.VALUE_PREFIX_UTF8, t.object)) {
                final Node vnode = context.valueNode(t.object);
                vnode.links.add(t);
                if (stableId) {
                    vnode.stableReferrers.add(Pair.of(this.subject, t.predicate));
                }
                this.values.computeIfAbsent(t.predicate, k -> new HashSet<>()).add(vnode);
                return;
            }
            super.add(context, t);
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && values.isEmpty();
        }

        public Stream<Node> getValues() {
            return this.values.values().stream().flatMap(Collection::stream);
        }

        @Override
        Stream<BlankNode> getLinked() {
            return Stream.concat(super.getLinked(), getValues());
        }
    }

    public static class NodeWithReferences extends NodeWithValues {
        final Set<NodeWithValues> references = new HashSet<>();

        public NodeWithReferences(ByteBuffer subject, boolean relativize, boolean stableId) {
            super(subject, relativize, stableId);
        }

        @Override
        public void add(ParsedDocument context, Triple t) {
            if (t.predicate.equals(ByteBuffer.wrap(Utils.WAS_DERIVED_FROM)) && Utils.isPrefix(Utils.REFERENCE_PREFIX_UTF8, t.object)) {
                final NodeWithValues rnode = context.referenceNode(t.object);
                rnode.links.add(t);
                if (stableId) {
                    rnode.stableReferrers.add(Pair.of(this.subject, t.predicate));
                }
                this.references.add(rnode);
                return;
            }
            super.add(context, t);
        }

        public Stream<NodeWithValues> getReferences() {
            return this.references.stream();
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && references.isEmpty();
        }

        @Override
        Stream<BlankNode> getLinked() {
            return Stream.concat(super.getLinked(), getReferences());
        }
    }

    private final Node root = new Node(ByteBuffer.wrap(new byte[]{}), false, true);
    final Map<ByteBuffer, BlankNode> allBNodes = new HashMap<>();
    final Map<ByteBuffer, Node> allValueNodes = new HashMap<>();
    final Map<ByteBuffer, NodeWithValues> allReferences = new HashMap<>();
    final Map<ByteBuffer, NodeWithReferences> allStatements = new HashMap<>();

    private ByteBuffer origRawDoc = ByteBuffer.allocate(0);
    private String id;

    ParsedDocument() {
        this(null);
    }

    ParsedDocument(String entityId) {
        this.id = entityId;
    }

    BlankNode bNode(ByteBuffer subject) {
        return this.allBNodes.computeIfAbsent(subject, k -> new BlankNode(subject, true, false));
    }

    Node valueNode(ByteBuffer subject) {
        return this.allValueNodes.computeIfAbsent(subject, k -> new Node(subject, true, false));
    }

    NodeWithValues referenceNode(ByteBuffer subject) {
        return this.allReferences.computeIfAbsent(subject, k -> new NodeWithValues(subject, true, true));
    }

    NodeWithReferences statementNode(ByteBuffer subject) {
        return this.allStatements.computeIfAbsent(subject, k -> new NodeWithReferences(subject, false, true));
    }

    public void add(Triple t) {
        // classify triple by subject
        if (Utils.isPrefix(Utils.BNODE_PREFIX_UTF8, t.subject)) {
            this.bNode(t.subject).add(this, t);
            return;
        }

        if (Utils.isPrefix(Utils.VALUE_PREFIX_UTF8, t.subject)) {
            this.valueNode(t.subject).add(this, t);
            return;
        }

        if (Utils.isPrefix(Utils.REFERENCE_PREFIX_UTF8, t.subject)) {
            this.referenceNode(t.subject).add(this, t);
            return;
        }

        if (Utils.isPrefix(Utils.STATEMENT_PREFIX_UTF8, t.subject)) {
            this.statementNode(t.subject).add(this, t);
            return;
        }

        // not special, add to other triples
        root.add(this, t);
    }

    public Node getRoot() {
        return root;
    }

    public String summarize() {
        return "ParsedDocument(stmtCount=" + this.allStatements.size() +
                " otherCount="+this.root.other.size() + ":" + this.root.bnodes.size() +
                " id="+this.getId()+
                ")";
    }

    public String getId() {
        return Objects.requireNonNullElse(this.id, "unknown");
    }

    public ByteBuffer getOrigRawDoc() {
        return this.origRawDoc.asReadOnlyBuffer();
    }

    private static <T extends BlankNode> void matchBySubject(Map<ByteBuffer, T> a, Map<ByteBuffer, T> b) {
        for (T aNode : a.values()) {
            final T bNode = b.get(aNode.subject);
            if (bNode != null) {
                aNode.matchedNode = bNode;
                bNode.matchedNode = aNode;
            }
        }
    }

    private static ImmutableSet<Pair<ByteBuffer,ByteBuffer>> stableRefKey(BlankNode n) {
        return ImmutableSet.copyOf(n.stableReferrers);
    }

    public <T extends BlankNode> void matchByStructure(Map<ByteBuffer, T> a, Map<ByteBuffer, T> b) {
        final Map<Set<Pair<ByteBuffer,ByteBuffer>>, Set<T>> aReferrer = a.values().stream()
                .collect(Collectors.toMap(ParsedDocument::stableRefKey, Collections::singleton, Sets::union));
        final Map<Set<Pair<ByteBuffer,ByteBuffer>>, Set<T>> bReferrer = b.values().stream()
                .collect(Collectors.toMap(ParsedDocument::stableRefKey, Collections::singleton, Sets::union));

        for (Map.Entry<Set<Pair<ByteBuffer, ByteBuffer>>, Set<T>> aEntry : aReferrer.entrySet()) {
            final Set<T> aCandidates = aEntry.getValue();
            final Set<T> bCandidates = bReferrer.get(aEntry.getKey());
            if (bCandidates == null) continue;

            // match best by content
            for (T aNode : aCandidates) {
                long best = 0;
                ArrayList<T> bestNodes = new ArrayList<>();
                final Set<ByteBuffer> objects = aNode.origOther.stream().map(t -> t.object).collect(Collectors.toSet());
                for (T bNode : bCandidates) {
                    long score = bNode.origOther.stream().filter(t -> objects.contains(t.object)).count();
                    if (score > best) {
                        bestNodes.clear();
                        best = score;
                    }
                    if (score == best) {
                        bestNodes.add(bNode);
                    }
                }

                for (T bNode : bestNodes) {
                    if (best == 0 && !bNode.isEmpty() && !aNode.isEmpty()) continue;
                    aNode.matchedNode = bNode;
                    bNode.matchedNode = aNode;
                }
            }

            // match unmatched empty nodes in b
            final Optional<T> anyA = aCandidates.stream().findAny();
            if (anyA.isPresent()) {
                for (T bNode : bCandidates) {
                    if (bNode.isEmpty() && bNode.matchedNode == null) bNode.matchedNode = anyA.get();
                }
            }

        }
    }

    public BlankNode findNode(ByteBuffer n) {
        return this.allBNodes.getOrDefault(n,
                this.allValueNodes.getOrDefault(n,
                        this.allReferences.getOrDefault(n,
                                this.allStatements.get(n))));
    }

    public boolean isOrphan(BlankNode n) {
        if (n.stableId) return false;
        if (n.orphan) return true;

        Set<BlankNode> toVisit = new HashSet<>();
        Set<ByteBuffer> visited = new HashSet<>();
        toVisit.add(n);
        while (!toVisit.isEmpty()) {
            final Iterator<BlankNode> it = toVisit.iterator();
            final BlankNode cur = it.next();
            it.remove();
            if (!cur.stableReferrers.isEmpty()) return false;
            visited.add(cur.subject);
            for (Triple t : n.links) {
                if (visited.contains(t.subject)) continue;
                final BlankNode r = findNode(t.subject);
                if (r != null && !r.stableId && !r.orphan) toVisit.add(r);
            }

        }
        for (ByteBuffer key : visited) {
            this.findNode(key).orphan = true;
        }
        return true;
    }

    private boolean emptyRef(ByteBuffer ref) {
        final Node n = this.allReferences.getOrDefault(ref, this.allStatements.get(ref));
        return n != null && n.isEmpty();
    }

    private void filterStableRef(ParsedDocument other) {
        Stream.concat(this.allBNodes.values().stream(), this.allValueNodes.values().stream()).forEach(node -> {
            node.stableReferrers.removeIf(ref -> this.emptyRef(ref.getKey()) || other.emptyRef(ref.getKey()));
        });
    }

    public void matchNodes(ParsedDocument other) {
        // match statements and references by ID
        matchBySubject(this.allStatements, other.allStatements);
        matchBySubject(this.allReferences, other.allReferences);

        // filter stable references
        this.filterStableRef(other);
        other.filterStableRef(this);

        // match value nodes and bnodes by structure since they don't have stable identifiers
        matchByStructure(this.allValueNodes, other.allValueNodes);
        matchByStructure(this.allBNodes, other.allBNodes);
    }

    public static boolean parse(final byte[] doc, ParsedDocument parsed) {
        int idx = 0;
        final ByteBuffer buffer = ByteBuffer.wrap(doc);
        parsed.origRawDoc = ByteBuffer.allocate(doc.length);
        parsed.origRawDoc.put(doc);
        parsed.origRawDoc.flip();
        while (idx < doc.length) {
            // look for the end of the triple
            int nextIdx = ArrayUtils.indexOf(doc, (byte) 0xa, idx);
            if (nextIdx == -1) nextIdx = doc.length;

            // find position where predicate (pStart) and object (oStart) begin
            final int pStart = ArrayUtils.indexOf(doc, (byte)0x20, idx) + 1;
            assert (pStart != 0);
            final int oStart = ArrayUtils.indexOf(doc, (byte)0x20, pStart + 1) + 1;
            assert (oStart != 0);

            if (oStart > nextIdx || pStart > nextIdx) {
                final ByteBuffer line = buffer.slice();
                line.position(idx);
                line.limit(nextIdx);
                System.err.println(Utils.bufferString(buffer));
                throw new RuntimeException("Invalid line: " + Utils.bufferString(line));
            }

            // define ByteBuffers for subject, predicate, object
            final ByteBuffer subject = buffer.slice();
            final ByteBuffer predicate = buffer.slice();
            ByteBuffer object = buffer.slice();
            subject.limit(pStart - 1);
            subject.position(idx);
            predicate.limit(oStart - 1);
            predicate.position(pStart);
            object.limit(nextIdx - 2);
            object.position(oStart);

            // SKIP if this entity is sameAs some other entity
            if (predicate.equals(ByteBuffer.wrap(Utils.SAME_AS_UTF8))) {
                if (parsed.id == null) {
                    final String subjectStr = Utils.bufferString(subject);
                    parsed.id = subjectStr.substring(subjectStr.lastIndexOf("/") + 1, subjectStr.length() - 1);
                }
                System.out.println("SKIP SAMEAS " + Utils.bufferString(object));
                return false;
            }

            // SKIP if schema:name, skos:prefLabel (not implemented)
            if (predicate.equals(ByteBuffer.wrap(Utils.SCHEMA_NAME_UTF8)) || predicate.equals(ByteBuffer.wrap(Utils.SKOS_PREFLABEL_UTF8))) {
                idx = nextIdx + 1;
                continue;
            }

            // SKIP missing metadata about sitelinks
            if (predicate.equals(ByteBuffer.wrap(Utils.WB_WIKIGROUP_UTF8)) || predicate.equals(ByteBuffer.wrap(Utils.SCHEMA_ISPARTOF_UTF8))) {
                idx = nextIdx + 1;
                continue;
            }

            // SKIP if predicate is normalized (not supported)
            if (Utils.isPrefix(Utils.DIRECT_NORMALIZED_UTF8,predicate) || Utils.isPrefix(Utils.QUALIFIER_NORMALIZED,predicate)
                    || Utils.isPrefix(Utils.REFERENCE_NORMALIZED,predicate) || Utils.isPrefix(Utils.STATEMENT_NORMALIZED,predicate)
                    || ByteBuffer.wrap(Utils.QUANTITY_NORMALIZED).equals(predicate)) {
                idx = nextIdx + 1;
                continue;
            }

            // SKIP if object is normalized
            if (Utils.isPrefix(Utils.DIRECT_NORMALIZED_UTF8,object) || Utils.isPrefix(Utils.QUALIFIER_NORMALIZED,object)
                    || Utils.isPrefix(Utils.REFERENCE_NORMALIZED,object) || Utils.isPrefix(Utils.STATEMENT_NORMALIZED,object)) {
                idx = nextIdx + 1;
                continue;
            }


            // SKIP owl things
            if (ByteBuffer.wrap(Utils.OWL_CLASS_UTF8).equals(object) || ByteBuffer.wrap(Utils.OWL_DATATYPE_UTF8).equals(object)
                || ByteBuffer.wrap(Utils.OWL_OBJECT_UTF8).equals(object) || ByteBuffer.wrap(Utils.OWL_RESTRICTION_UTF8).equals(object)
                || ByteBuffer.wrap(Utils.OWL_THING_UTF8).equals(object) || ByteBuffer.wrap(Utils.OWL_ON_PROPERTY_UTF8).equals(predicate)
                || ByteBuffer.wrap(Utils.OWL_COMPLEMENT_UTF8).equals(predicate)) {
                idx = nextIdx + 1;
                continue;
            }

            // skip GeoAutoPrecision
            if (object.equals(ByteBuffer.wrap(Utils.GEO_AUTO_PRECISION))) {
                idx = nextIdx + 1;
                continue;
            }

            // SKIP EntityData, but extract entity id (not implemented)
            if (Utils.isPrefix(Utils.ENTITY_DATA_UTF8, subject)) {
                if (parsed.id == null) {
                    final String subjectStr = Utils.bufferString(subject);
                    parsed.id = subjectStr.substring(subjectStr.lastIndexOf("/") + 1, subjectStr.length() - 1);
                }
                idx = nextIdx + 1;
                continue;
            }

            // SKIP statements concerning dump version
            if (ByteBuffer.wrap(Utils.WIKIBASE_DUMP).equals(subject)) {
                idx = nextIdx + 1;
                continue;
            }

            // canonicalize values for objects
            if (doc[oStart] == 0x22 && nextIdx - 5 > oStart) {
                int typeStart = nextIdx - 5;
                int langStart = -1;
                boolean canhavelang = true;
                for (; typeStart > oStart; --typeStart) {
                    if (canhavelang && doc[typeStart] == 0x40) {
                        canhavelang = false;
                        langStart = typeStart;
                    }
                    if (doc[typeStart] == 0x22  || doc[typeStart] == 0x3e) canhavelang = false;
                    if (doc[typeStart] == 0x22 && doc[typeStart + 1] == 0x5e && doc[typeStart + 2] == 0x5e) break;
                }

                // lower case lang tag
                if (langStart != -1) {
                    for (int i = langStart; i < object.limit(); ++i) {
                        byte x = doc[i];
                        if (x > 0x40 && x < 0x5b) {
                            doc[i] = (byte) (x | (byte)0x20);
                        }
                    }
                }

                // handle typed string literals
                if (typeStart != oStart) {
                    final ByteBuffer type = buffer.slice();
                    type.limit(nextIdx - 2);
                    type.position(typeStart + 3);

                    ByteBuffer value = buffer.slice();
                    value.limit(typeStart);
                    value.position(oStart + 1);

                    if (type.equals(ByteBuffer.wrap(Utils.XSD_DECIMAL_UTF8))) {
                        // remove leading +
                        if (doc[oStart + 1] == (byte) 0x2b) {
                            System.arraycopy(doc, oStart + 2, doc, oStart + 1, object.remaining() - 2);
                            object.limit(nextIdx - 3);
                        }
                    } else if (type.equals(ByteBuffer.wrap(Utils.XSD_DOUBLE_UTF8))) {
                        final double d = Double.parseDouble(Utils.bufferString(value));
                        value.limit(object.limit());
                        value.put((Math.round(d) + "\"^^<c/double>").getBytes(StandardCharsets.UTF_8));
                        object.limit(value.position());
                    } else if (type.equals(ByteBuffer.wrap(Utils.WKT_LITERAL))) {
                        final Matcher m = Utils.POINT_PATTERN.matcher(Utils.bufferString(value));

                        if (m.matches()) {
                            final MatchResult result = m.toMatchResult();

                            final String prefix = result.group(1);
                            float f1 = Float.parseFloat(result.group(2));
                            float f2 = Float.parseFloat(result.group(3));
                            value.limit(object.limit());
                            value.put(((prefix == null ? "" : prefix) + "Point(" + Math.round(f1) + " " + Math.round(f2) + ")\"^^<c/wkt>").getBytes(StandardCharsets.US_ASCII));
                            object.limit(value.position());
                        }
                    } else if (type.equals(ByteBuffer.wrap(Utils.MATHML_UTF8))) { // math is not implemented correctly, replace the value by placeholder
                        object = StandardCharsets.US_ASCII.encode("\"BLANK\"^^<c/math>");
                    }
                }

                if (ByteArrayUtil.find(doc, object.position(), object.limit(), (byte)0x5c) != -1) {
                    object = StandardCharsets.UTF_8.encode(NTriplesUtil.unescapeString(Utils.bufferString(object)));
                }
            } else if (doc[oStart] == 0x3c) { // canonicalize URI
                ByteBuffer value = buffer.slice();
                value.limit(object.limit() - 1);
                value.position(object.position() + 1);
                String iri = Utils.bufferString(value);
                if (iri.contains("http://commons.wikimedia.org/wiki/Special:FilePath")) {
                    iri = iri.replace("%20", "_");
                } else if (iri.contains("http://commons.wikimedia.org/data/main/")) {
                    iri = iri.replace("+", "_");
                }
                object = StandardCharsets.US_ASCII.encode("<" + iri + ">");
                Utils.replaceUrlEscapes(object);
            }

            if (doc[subject.position()] == 0x3c) {
                Utils.replaceUrlEscapes(subject);
            }

            // add triple to document
            final Triple triple = new Triple(subject, predicate, object);
            parsed.add(triple);

            idx = nextIdx + 1;
        }

        return true;
    }
}
