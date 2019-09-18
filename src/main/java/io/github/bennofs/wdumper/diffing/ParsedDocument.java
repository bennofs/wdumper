package io.github.bennofs.wdumper.diffing;

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
        Set<Triple> links = new HashSet<>();
        final Set<Triple> other = new HashSet<>();
        final Set<Triple> origOther = new HashSet<>();
        BlankNode matchedNode = null;
        private boolean relativize;

        public BlankNode(ByteBuffer subject, boolean relativize) {
            this.subject = subject;
            this.relativize = relativize;
        }

        public void add(ParsedDocument context, Triple t) {
            if (relativize && t.subject.equals(this.subject)) {
                t = new Triple(Utils.SUBJECT_SELF, t.predicate, t.object);
            }
            other.add(t);
            origOther.add(t);
        }

        public boolean isEmpty() {
            return this.other.isEmpty();
        }

        public void removeMatchingOther(BlankNode b) {
            other.removeIf(b.other::remove);
        }

        @Override
        public String toString() {
            return "Node(subject=" + Utils.bufferString(this.subject) +")";
        }
    }

    public static class Node extends BlankNode {
        final Map<ByteBuffer, Set<BlankNode>> bnodes = new HashMap<>();

        public Node(ByteBuffer subject, boolean relativize) {
            super(subject, relativize);
        }

        @Override
        public void add(ParsedDocument context, Triple t) {
            if (Utils.isPrefix(Utils.BNODE_PREFIX_UTF8, t.object)) {
                final BlankNode bnode = context.bNode(t.object);
                this.bnodes.computeIfAbsent(t.predicate, k -> new HashSet<>()).add(bnode);
                bnode.links.add(t);
                return;
            }
            super.add(context, t);
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && this.bnodes.isEmpty();
        }

        public void consumeMatchingBNodes(Node n, BiConsumer<BlankNode, BlankNode> match) {
            consumeMatchingNodes(this.bnodes, n.bnodes, match);
        }

        public Stream<BlankNode> getBNodes() {
            return this.bnodes.values().stream().flatMap(Collection::stream);
        }
    }

    public static class NodeWithValues extends Node {
        final Map<ByteBuffer, Set<Node>> values = new HashMap<>();

        public NodeWithValues(ByteBuffer subject, boolean relativize) {
            super(subject, relativize);
        }

        @Override
        public void add(ParsedDocument context, Triple t) {
            if (Utils.isPrefix(Utils.VALUE_PREFIX_UTF8, t.object)) {
                final Node vnode = context.valueNode(t.object);
                vnode.links.add(t);
                this.values.computeIfAbsent(t.predicate, k -> new HashSet<>()).add(vnode);
                return;
            }
            super.add(context, t);
        }

        @Override
        public boolean isEmpty() {
            return super.isEmpty() && values.isEmpty();
        }

        public void consumeMatchingValues(NodeWithValues n, BiConsumer<Node, Node> match) {
            consumeMatchingNodes(this.values, n.values, match);
        }

        public Stream<Node> getValues() {
            return this.values.values().stream().flatMap(Collection::stream);
        }
    }

    public static class NodeWithReferences extends NodeWithValues {
        final Set<NodeWithValues> references = new HashSet<>();

        public NodeWithReferences(ByteBuffer subject, boolean relativize) {
            super(subject, relativize);
        }

        @Override
        public void add(ParsedDocument context, Triple t) {
            if (t.predicate.equals(ByteBuffer.wrap(Utils.WAS_DERIVED_FROM)) && Utils.isPrefix(Utils.REFERENCE_PREFIX_UTF8, t.object)) {
                final NodeWithValues rnode = context.referenceNode(t.object);
                rnode.links.add(t);
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

        public void consumeMatchingReferences(NodeWithReferences n, BiConsumer<NodeWithValues, NodeWithValues> match) {
            consumeMatchingNodes(this.references, n.references, match);
        }
    }

    private final Node root = new Node(ByteBuffer.wrap(new byte[]{}), false);
    private final Map<ByteBuffer, BlankNode> allNodes = new HashMap<>();
    private final Map<ByteBuffer, NodeWithReferences> allStatements = new HashMap<>();
    private String id;

    ParsedDocument() {
        this(null);
    }

    ParsedDocument(String entityId) {
        this.id = entityId;
    }

    BlankNode bNode(ByteBuffer subject) {
        return this.allNodes.computeIfAbsent(subject, k -> new BlankNode(subject, true));
    }

    Node valueNode(ByteBuffer subject) {
        return (Node)this.allNodes.computeIfAbsent(subject, k -> new Node(subject, true));
    }

    NodeWithValues referenceNode(ByteBuffer subject) {
        return (NodeWithValues)this.allNodes.computeIfAbsent(subject, k -> new NodeWithValues(subject, true));
    }

    NodeWithReferences statementNode(ByteBuffer subject) {
        return this.allStatements.computeIfAbsent(subject, k -> new NodeWithReferences(subject, false));
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

    public void consumeMatchingStatements(ParsedDocument other, BiConsumer<NodeWithReferences, NodeWithReferences> consumer) {
        for (Iterator<Map.Entry<ByteBuffer, NodeWithReferences>> it = allStatements.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<ByteBuffer, NodeWithReferences> entry = it.next();
            final NodeWithReferences otherStmt = other.allStatements.remove(entry.getKey());
            if (otherStmt == null) continue;

            it.remove();
            consumer.accept(entry.getValue(), otherStmt);
        }
    }

    public Node getRoot() {
        return root;
    }

    public Stream<BlankNode> getAllNodes() {
        return allNodes.values().stream();
    }

    public BlankNode findNode(ByteBuffer subject) {
        return this.allNodes.get(subject);
    }

    public String summarize() {
        return "ParsedDocument(stmtCount=" + this.allStatements.size() +
                " nodeCount="+this.allNodes.size() +
                " otherCount="+this.root.other.size() + ":" + this.root.bnodes.size() +
                " id="+this.getId()+
                ")";
    }

    public String getId() {
        return Objects.requireNonNullElse(this.id, "unknown");
    }

    private static <T extends BlankNode> boolean allEqualOthers(Collection<T> c) {
        final T first = c.iterator().next();
        return c.stream().allMatch(n -> n.origOther.equals(first.origOther));
    }

    private static <T extends BlankNode> boolean resolveMatching(Pair<Set<T>, Set<T>> matching, Set<T> aCandidates, Set<T> bCandidates, BiConsumer<T, T> match) {
        final Set<T> matchA = matching.getLeft();
        final Set<T> matchB = matching.getRight();

        // no match, but also cannot match this any further
        if (matchA.size() == 0 || matchB.size() == 0) return true;

        // can only match if both sides contain equal nodes
        if (allEqualOthers(matchA) && allEqualOthers(matchB)) {
            Iterator<T> aIt = matchA.iterator();
            Iterator<T> bIt = matchB.iterator();
            while (aIt.hasNext() && bIt.hasNext()) {
                final T aNode = aIt.next();
                final T bNode = bIt.next();
                aCandidates.remove(aNode);
                bCandidates.remove(bNode);
                match.accept(aNode, bNode);
            }

            // match resolved
            return true;
        }

        // no match, add it to queue again
        return false;
    }

    private static <T extends BlankNode> void consumeMatchingNodes(Set<T> aCandidates, Set<T> bCandidates, BiConsumer<T,T> match) {
        final Deque<T> noDataB = new ArrayDeque<>();

        final Map<ByteBuffer, Set<T>> bIndex = bCandidates
                .stream()
                .flatMap(n -> {
                    if (n.origOther.size() == 0) {
                        noDataB.add(n);
                    }
                    return n.origOther.stream().map(t -> Pair.of(t.object, n));
                })
                .collect(Collectors.toMap(Pair::getKey, x -> Collections.singleton(x.getValue()), Sets::union));

        // match by unique object
        final Map<ByteBuffer, Set<T>> aIndex = new HashMap<>();
        for (Iterator<T> it = aCandidates.iterator(); it.hasNext(); ) {
            T aNode = it.next();

            if (aNode.matchedNode != null && bCandidates.remove(aNode.matchedNode)) {
                it.remove();
                match.accept(aNode, (T)aNode.matchedNode);
                continue;
            }

            for (Triple t : aNode.origOther) {
                final Set<T> matches = bIndex.get(t.object);
                if (matches != null && matches.size() == 1) {
                    final T bNode = matches.iterator().next();
                    bIndex.remove(t.object);
                    it.remove();
                    bCandidates.remove(bNode);
                    match.accept(aNode, bNode);
                    break;
                }
                aIndex.computeIfAbsent(t.object, k -> new HashSet<>()).add(aNode);
            }
        }

        // match by unique combination of objects
        final Pair<Set<T>, Set<T>> initialPartition = Pair.of(
                new HashSet<>(aCandidates),
                new HashSet<>(bCandidates)
        );
        if (resolveMatching(initialPartition, aCandidates, bCandidates, match)) {
            return;
        }

        List<Pair<Set<T>, Set<T>>> partitions = Collections.singletonList(initialPartition);
        for (Map.Entry<ByteBuffer, Set<T>> entry : aIndex.entrySet()) {
            final Set<T> matchAs = entry.getValue();
            final Set<T> matchBs = bIndex.get(entry.getKey());
            if (matchBs == null) continue;

            List<Pair<Set<T>, Set<T>>> newPartitions = new ArrayList<>();
            for (Pair<Set<T>, Set<T>> partition : partitions) {
                final Pair<Set<T>, Set<T>> matching = Pair.of(
                        Sets.intersection(partition.getLeft(), matchAs),
                        Sets.intersection(partition.getRight(), matchBs)
                );
                final Pair<Set<T>, Set<T>> other = Pair.of(
                        Sets.difference(partition.getLeft(), matchAs),
                        Sets.difference(partition.getRight(), matchBs)
                );

                // if this split only causes unmatchable partitions, ignore it
                if (matching.getLeft().size() * matching.getRight().size() == 0
                        && other.getLeft().size() * other.getRight().size() == 0) {
                    newPartitions.add(partition);
                    continue;
                }

                if (!resolveMatching(matching, aCandidates, bCandidates, match)) {
                    newPartitions.add(matching);
                }
                if (!resolveMatching(other, aCandidates, bCandidates, match)) {
                    newPartitions.add(other);
                }
            }
            partitions = newPartitions;
            if (partitions.isEmpty()) break;
        }
    }

    private static <T extends BlankNode> void consumeMatchingNodes(Map<ByteBuffer, Set<T>> a, Map<ByteBuffer, Set<T>> b, BiConsumer<T, T> match) {
        for (Iterator<Map.Entry<ByteBuffer, Set<T>>> it = a.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<ByteBuffer, Set<T>> groupEntry = it.next();
            final Set<T> aCandidates = groupEntry.getValue();
            final Set<T> bCandidates = b.get(groupEntry.getKey());

            if (bCandidates == null) continue;

            if (aCandidates.size() == 1 && bCandidates.size() == 1) {
                match.accept(aCandidates.iterator().next(), bCandidates.iterator().next());
                b.remove(groupEntry.getKey());
                it.remove();
                continue;
            }

            // slow path, try to disambiguate
            consumeMatchingNodes(aCandidates, bCandidates, match);

            if (bCandidates.isEmpty()) {
                b.remove(groupEntry.getKey());
            }

            if (aCandidates.isEmpty()) {
                it.remove();
            }
        }
    }

    public static boolean parse(final byte[] doc, ParsedDocument parsed) {
        int idx = 0;
        final ByteBuffer buffer = ByteBuffer.wrap(doc);
        while (idx < doc.length) {
            // look for the end of the triple
            int nextIdx = ArrayUtils.indexOf(doc, (byte) 0xa, idx);
            if (nextIdx == -1) nextIdx = doc.length;

            // find position where predicate (pStart) and object (oStart) begin
            final int pStart = ArrayUtils.indexOf(doc, (byte)0x20, idx) + 1;
            assert (pStart != 0);
            final int oStart = ArrayUtils.indexOf(doc, (byte)0x20, pStart + 1) + 1;
            assert (oStart != 0);

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

            // SKIP owl things
            if (Utils.isPrefix(Utils.OWL_UTF8, predicate) || Utils.isPrefix(Utils.OWL_UTF8, object)) {
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
