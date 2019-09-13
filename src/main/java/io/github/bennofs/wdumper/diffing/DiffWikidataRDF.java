package io.github.bennofs.wdumper.diffing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.rdf4j.common.io.ByteArrayUtil;
import org.eclipse.rdf4j.rio.ntriples.NTriplesUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DiffWikidataRDF {
    public static class ValueNode {
        final Set<Triple> triples = new HashSet<>();
        final Map<ByteBuffer, Triple> statementLinks = new HashMap<>();
        public boolean onlyNormalizedLink = true;
    }

    private static final HashMap<ByteBuffer, ByteBuffer> serializedToDumpValue = new HashMap<>();
    private static final HashMap<ByteBuffer, ByteBuffer> serializedToDumpReference = new HashMap<>();
    private static final HashMap<ByteBuffer, ByteBuffer> serializedToDumpBNode = new HashMap<>();

    public static class Parsed {
        final public Set<Triple> otherTriples;
        final public Map<ByteBuffer, Set<Triple>> statementTriples;
        final public Map<ByteBuffer, ValueNode> valueNodes;
        final public Map<ByteBuffer, Set<Triple>> referenceTriples;
        final public Map<ByteBuffer, Set<Triple>> bnodeTriples;

        public Parsed() {
            otherTriples = new HashSet<>();
            statementTriples = new HashMap<>();
            valueNodes = new HashMap<>();
            referenceTriples = new HashMap<>();
            bnodeTriples = new HashMap<>();
        }

        public String getId() {
            for (Triple t : otherTriples) {
                if (ByteBuffer.wrap(ONTOLOGY_ITEM).equals(t.object)) {
                    final String subject = new String(bufferBytes(t.subject), StandardCharsets.US_ASCII);
                    return subject.substring(subject.lastIndexOf("/") + 1, subject.length() - 1);
                }
            }
            return "unknown";
        }

        private int removeNormalizedOnlyValues() {
            Set<Map.Entry<ByteBuffer, ValueNode>> toRemove = valueNodes.entrySet().stream()
                    .filter(e -> e.getValue().onlyNormalizedLink)
                    .collect(Collectors.toSet());
            valueNodes.entrySet().removeAll(toRemove);
            return toRemove.size();
        }
    }

    static final byte[] ENTITY_DATA_UTF8;
    static final byte[] DIRECT_NORMALIZED_UTF8;
    static final byte[] OWL_UTF8;
    static final byte[] XSD_DECIMAL_UTF8;
    static final byte[] SAME_AS_UTF8;
    static final byte[] XSD_DOUBLE_UTF8;
    static final byte[] WKT_LITERAL;
    static final byte[] STATEMENT_PREFIX_UTF8;
    static final byte[] REFERENCE_PREFIX_UTF8;
    static final byte[] BNODE_PREFIX_UTF8;
    static final byte[] VALUE_PREFIX_UTF8;
    static final byte[] SCHEMA_NAME_UTF8;
    static final byte[] SKOS_PREFLABEL_UTF8;
    static final byte[] SCHEMA_ISPARTOF_UTF8;
    static final byte[] WB_WIKIGROUP_UTF8;
    static final byte[] MATHML_UTF8;
    static final byte[] ONTOLOGY_ITEM;
    static final byte[] STATEMENT_NORMALIZED;
    static final byte[] REFERENCE_NORMALIZED;
    static final byte[] QUALIFIER_NORMALIZED;
    static final byte[] WAS_DERIVED_FROM;
    static final Pattern POINT_PATTERN = Pattern.compile("(<[^>]*> )?Point\\(([^ ]*) ([^ ]*)\\)");

    static {
        ENTITY_DATA_UTF8 = "<https://www.wikidata.org/wiki/Special:EntityData/".getBytes(StandardCharsets.UTF_8);
        DIRECT_NORMALIZED_UTF8 = "<http://www.wikidata.org/prop/direct-normalized".getBytes(StandardCharsets.UTF_8);
        OWL_UTF8 = "<http://www.w3.org/2002/07/owl#".getBytes(StandardCharsets.UTF_8);
        XSD_DECIMAL_UTF8 = "<http://www.w3.org/2001/XMLSchema#decimal>".getBytes(StandardCharsets.UTF_8);
        SAME_AS_UTF8 = "<http://www.w3.org/2002/07/owl#sameAs>".getBytes(StandardCharsets.UTF_8);
        XSD_DOUBLE_UTF8 = "<http://www.w3.org/2001/XMLSchema#double>".getBytes(StandardCharsets.UTF_8);
        WKT_LITERAL = "<http://www.opengis.net/ont/geosparql#wktLiteral>".getBytes(StandardCharsets.UTF_8);
        STATEMENT_PREFIX_UTF8 = "<http://www.wikidata.org/entity/statement/".getBytes(StandardCharsets.UTF_8);
        REFERENCE_PREFIX_UTF8 = "<http://www.wikidata.org/reference/".getBytes(StandardCharsets.UTF_8);
        BNODE_PREFIX_UTF8 = "_:".getBytes(StandardCharsets.UTF_8);
        VALUE_PREFIX_UTF8 = "<http://www.wikidata.org/value/".getBytes(StandardCharsets.UTF_8);
        SCHEMA_NAME_UTF8 = "<http://schema.org/name>".getBytes(StandardCharsets.UTF_8);
        SKOS_PREFLABEL_UTF8 = "<http://www.w3.org/2004/02/skos/core#prefLabel>".getBytes(StandardCharsets.UTF_8);
        SCHEMA_ISPARTOF_UTF8 = "<http://schema.org/isPartOf>".getBytes(StandardCharsets.UTF_8);
        WB_WIKIGROUP_UTF8 = "<http://wikiba.se/ontology#wikiGroup>".getBytes(StandardCharsets.UTF_8);
        MATHML_UTF8 = "<http://www.w3.org/1998/Math/MathML>".getBytes(StandardCharsets.UTF_8);
        ONTOLOGY_ITEM = "<http://wikiba.se/ontology#Item>".getBytes(StandardCharsets.UTF_8);
        STATEMENT_NORMALIZED = "<http://www.wikidata.org/prop/statement/value-normalized/".getBytes(StandardCharsets.UTF_8);
        QUALIFIER_NORMALIZED = "<http://www.wikidata.org/prop/qualifier/value-normalized/".getBytes(StandardCharsets.UTF_8);
        REFERENCE_NORMALIZED = "<http://www.wikidata.org/prop/reference/value-normalized/".getBytes(StandardCharsets.UTF_8);
        WAS_DERIVED_FROM = "<http://www.w3.org/ns/prov#wasDerivedFrom>".getBytes(StandardCharsets.UTF_8);
    }

    private static final class Triple {
        final ByteBuffer subject;
        final ByteBuffer predicate;
        final ByteBuffer object;

        public Triple(ByteBuffer subject, ByteBuffer predicate, ByteBuffer object) {
            this.subject = subject;
            this.predicate = predicate;
            this.object = object;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Triple triple = (Triple) o;
            return subject.equals(triple.subject) &&
                    predicate.equals(triple.predicate) &&
                    object.equals(triple.object);
        }

        @Override
        public int hashCode() {
            return Objects.hash(subject, predicate, object);
        }

        public String toString() {
            return new String(bufferBytes(subject)) + " " + new String(bufferBytes(predicate)) + " " + new String(bufferBytes(object)) + " .";
        }
    }

    public DiffWikidataRDF() {
    }

    public static boolean parse(final byte[] doc, Parsed parsed) {
        int idx = 0;
        final ByteBuffer buffer = ByteBuffer.wrap(doc);
        while (idx < doc.length) {
            int nextIdx = ArrayUtils.indexOf(doc, (byte) 0xa, idx);
            if (nextIdx == -1) nextIdx = doc.length;

            // skip entity data triples
            if (idx + ENTITY_DATA_UTF8.length < nextIdx && Arrays.equals(
                    doc, idx, idx + ENTITY_DATA_UTF8.length,
                    ENTITY_DATA_UTF8, 0, ENTITY_DATA_UTF8.length)) {
                idx = nextIdx + 1;
                continue;
            }

            final int pStart = ArrayUtils.indexOf(doc, (byte)0x20, idx) + 1;
            assert (pStart != 0);
            if (pStart + DIRECT_NORMALIZED_UTF8.length < nextIdx && Arrays.equals(
                    doc, pStart, pStart + DIRECT_NORMALIZED_UTF8.length,
                    DIRECT_NORMALIZED_UTF8, 0, DIRECT_NORMALIZED_UTF8.length
            )) {
                idx = nextIdx + 1;
                continue;
            }
            final int oStart = ArrayUtils.indexOf(doc, (byte)0x20, pStart + 1) + 1;
            assert (oStart != 0);
            if (oStart + OWL_UTF8.length < nextIdx && Arrays.equals(
                    doc, oStart, oStart + OWL_UTF8.length,
                    OWL_UTF8, 0, OWL_UTF8.length
            )) {
                idx = nextIdx + 1;
                continue;
            }

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
            if (predicate.equals(ByteBuffer.wrap(SAME_AS_UTF8))) {
                System.out.println("SKIP SAMEAS " + new String(bufferBytes(subject), StandardCharsets.US_ASCII));
                return false;
            }

            // SKIP if schema:name, skos:prefLabel (not implemented)
            if (predicate.equals(ByteBuffer.wrap(SCHEMA_NAME_UTF8)) || predicate.equals(ByteBuffer.wrap(SKOS_PREFLABEL_UTF8))) {
                idx = nextIdx + 1;
                continue;
            }

            // SKIP missing metadata about sitelinks
            if (predicate.equals(ByteBuffer.wrap(WB_WIKIGROUP_UTF8)) || predicate.equals(ByteBuffer.wrap(SCHEMA_ISPARTOF_UTF8))) {
                idx = nextIdx + 1;
                continue;
            }

            // SKIP if predicate is normalized (not supported)
            if (isPrefix(QUALIFIER_NORMALIZED,predicate) || isPrefix(REFERENCE_NORMALIZED,predicate) || isPrefix(STATEMENT_NORMALIZED,predicate)) {
                idx = nextIdx + 1;
                continue;
            }

            // SKIP owl things
            if (isPrefix(OWL_UTF8, predicate)) {
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

                if (typeStart != oStart) {
                    final ByteBuffer type = buffer.slice();
                    type.limit(nextIdx - 2);
                    type.position(typeStart + 3);

                    ByteBuffer value = buffer.slice();
                    value.limit(typeStart);
                    value.position(oStart + 1);

                    if (type.equals(ByteBuffer.wrap(XSD_DECIMAL_UTF8))) {
                        // remove leading +
                        if (doc[oStart + 1] == (byte) 0x2b) {
                            System.arraycopy(doc, oStart + 2, doc, oStart + 1, object.remaining() - 2);
                            object.limit(nextIdx - 3);
                        }
                    } else if (type.equals(ByteBuffer.wrap(XSD_DOUBLE_UTF8))) {
                        final double d = Double.parseDouble(new String(bufferBytes(value), StandardCharsets.UTF_8));
                        value.limit(object.limit());
                        value.put((d + "\"^^<c/double>").getBytes(StandardCharsets.UTF_8));
                        object.limit(value.position());
                    } else if (type.equals(ByteBuffer.wrap(WKT_LITERAL))) {
                        final Matcher m = POINT_PATTERN.matcher(new String(bufferBytes(value), StandardCharsets.US_ASCII));

                        if (m.matches()) {
                            final MatchResult result = m.toMatchResult();

                            final String prefix = result.group(1);
                            float f1 = Float.parseFloat(result.group(2));
                            float f2 = Float.parseFloat(result.group(3));
                            value.limit(object.limit());
                            value.put(((prefix == null ? "" : prefix) + "Point(" + Math.round(f1) + " " + Math.round(f2) + ")\"^^<c/wkt>").getBytes(StandardCharsets.US_ASCII));
                            object.limit(value.position());
                        }
                    } else if (type.equals(ByteBuffer.wrap(MATHML_UTF8))) { // math is not implemented correctly, replace the value by placeholder
                        object = StandardCharsets.US_ASCII.encode("\"BLANK\"^^<c/math>");
                    }
                }

                if (ByteArrayUtil.find(doc, object.position(), object.limit(), (byte)0x5c) != -1) {
                    object = StandardCharsets.UTF_8.encode(NTriplesUtil.unescapeString(new String(bufferBytes(object), StandardCharsets.UTF_8)));
                }
            } else if (doc[oStart] == 0x3c) { // canonicalize URI
                ByteBuffer value = buffer.slice();
                value.limit(object.limit() - 1);
                value.position(object.position() + 1);
                String iri = new String(bufferBytes(value), StandardCharsets.US_ASCII);
                if (iri.contains("http://commons.wikimedia.org/wiki/Special:FilePath")) {
                    iri = iri.replace("%20", "_");
                } else if (iri.contains("http://commons.wikimedia.org/data/main/")) {
                    iri = iri.replace("+", "_");
                }
                try {
                    URI url = new URI(iri);
                    final URI canonicalize = new URI(url.getScheme(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getFragment());
                    iri = canonicalize.toString();
                } catch (URISyntaxException ignored) {
                }
                iri = iri
                        .replaceAll("%28", "(")
                        .replaceAll("%29", ")")
                        .replaceAll("%2C", ",")
                        .replaceAll("%21", "!")
                        .replaceAll("%7E", "~")
                        .replaceAll("%3B", ";")
                        .replaceAll("%7B", "{")
                        .replaceAll("%7C","|")
                        .replaceAll("%7D", "}");
                object = StandardCharsets.US_ASCII.encode("<" + iri + ">");
            }

            if (doc[subject.position()] == 0x3c) {
                replaceAll(subject, "%28".getBytes(StandardCharsets.US_ASCII), "(".getBytes(StandardCharsets.US_ASCII));
                replaceAll(subject, "%29".getBytes(StandardCharsets.US_ASCII), ")".getBytes(StandardCharsets.US_ASCII));
                replaceAll(subject, "%2C".getBytes(StandardCharsets.US_ASCII), ",".getBytes(StandardCharsets.US_ASCII));
                replaceAll(subject, "%21".getBytes(StandardCharsets.US_ASCII), "!".getBytes(StandardCharsets.US_ASCII));
                replaceAll(subject, "%7E".getBytes(StandardCharsets.US_ASCII), "~".getBytes(StandardCharsets.US_ASCII));
                replaceAll(subject, "%3B".getBytes(StandardCharsets.US_ASCII), ";".getBytes(StandardCharsets.US_ASCII));
                replaceAll(subject, "%7B".getBytes(StandardCharsets.US_ASCII), "{".getBytes(StandardCharsets.US_ASCII));
                replaceAll(subject, "%7C".getBytes(StandardCharsets.US_ASCII), "|".getBytes(StandardCharsets.US_ASCII));
                replaceAll(subject, "%7D".getBytes(StandardCharsets.US_ASCII), "}".getBytes(StandardCharsets.US_ASCII));
            }

            // categorize the triple
            final Triple triple = new Triple(subject, predicate, object);
            if (isPrefix(BNODE_PREFIX_UTF8, subject)) {
                parsed.bnodeTriples.computeIfAbsent(subject, x -> new HashSet<>()).add(triple);
            } else if (isPrefix(BNODE_PREFIX_UTF8, object)) {
                parsed.bnodeTriples.computeIfAbsent(object, x -> new HashSet<>()).add(triple);
            }

            if (isPrefix(VALUE_PREFIX_UTF8, subject)) {
                parsed.valueNodes.computeIfAbsent(subject, x -> new ValueNode()).triples.add(triple);
            } else if (isPrefix(VALUE_PREFIX_UTF8, object)) {
                final ValueNode v = parsed.valueNodes.computeIfAbsent(object, x -> new ValueNode());
                v.onlyNormalizedLink = v.onlyNormalizedLink && (isPrefix(STATEMENT_NORMALIZED, predicate)
                        || isPrefix(REFERENCE_NORMALIZED, predicate)
                        || isPrefix(QUALIFIER_NORMALIZED, predicate));
                v.triples.add(triple);
                if (isPrefix(STATEMENT_PREFIX_UTF8, subject)) {
                    v.statementLinks.put(subject, triple);
                }
            } else if (isPrefix(STATEMENT_PREFIX_UTF8, subject)) {
                parsed.statementTriples.computeIfAbsent(subject, x -> new HashSet<>()).add(triple);
            } else if (isPrefix(STATEMENT_PREFIX_UTF8, object)) {
                parsed.statementTriples.computeIfAbsent(object, x -> new HashSet<>()).add(triple);
            } else if (isPrefix(REFERENCE_PREFIX_UTF8, subject)) {
                parsed.referenceTriples.computeIfAbsent(subject, x -> new HashSet<>()).add(triple);
            } else if (isPrefix(REFERENCE_PREFIX_UTF8, object)) {
                parsed.referenceTriples.computeIfAbsent(object, x -> new HashSet<>()).add(triple);
            } else {
                parsed.otherTriples.add(triple);
            }

            idx = nextIdx + 1;
        }

        return true;
    }

    private static void replaceAll(ByteBuffer buf, byte[] from, byte[] to) {
        int d = from.length - to.length;
        if (d < 0) throw new IllegalArgumentException("from must be longer than to");
        int prevIdx = buf.position();
        while (true) {
            int idx = ByteArrayUtil.find(buf.array(), prevIdx, buf.limit(), from);
            if (idx == -1) return;
            System.arraycopy(to, 0, buf.array(), idx, to.length);
            if (d != 0) {
                System.arraycopy(buf.array(), idx + from.length, buf.array(), idx + to.length, buf.limit() - idx - from.length);
                buf.limit(buf.limit() - d);
            }
            prevIdx = idx + to.length;
        }
    }

    private static boolean isPrefix(byte[] prefix, ByteBuffer buf) {
        if (buf.remaining() < prefix.length) return false;
        return Arrays.equals(prefix, 0, prefix.length, buf.array(), buf.position(), buf.position() + prefix.length);
    }

    private static byte[] bufferBytes(ByteBuffer b)  {
        final byte[] out = new byte[b.remaining()];
        b.mark();
        b.get(out);
        b.reset();
        return out;
    }

    private static Map<ImmutableSet<ByteBuffer>, Set<ByteBuffer>> computeReferrers(Map<ByteBuffer, ValueNode> values) {
        final Map<ImmutableSet<ByteBuffer>, Set<ByteBuffer>> referrers = new HashMap<>();
        for (Map.Entry<ByteBuffer, ValueNode> entry : values.entrySet()) {
            referrers.computeIfAbsent(ImmutableSet.copyOf(entry.getValue().statementLinks.keySet()), x -> new HashSet<>()).add(entry.getKey());
        }
        return referrers;
    }

    private static Map<ByteBuffer, Set<ByteBuffer>> indexByObject(Stream<Map.Entry<ByteBuffer, Set<Triple>>> triples, HashMap<ByteBuffer, Integer> frequency) {
        return triples
                .flatMap(e -> e.getValue().stream().map(t -> {
                    frequency.compute(t.object, (k, x) -> x == null ? 1 : x + 1);
                    return Map.entry(t.object, Sets.newHashSet(e.getKey()));
                }))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a,b) -> { a.addAll(b); return a; }));
    }

    private static void matchByObjects(Set<ByteBuffer> valsDump, Set<ByteBuffer> valsSer, Function<ByteBuffer, Set<Triple>> getDump, Function<ByteBuffer, Set<Triple>> getSer, BiConsumer<ByteBuffer, ByteBuffer> match) {
        if (valsDump.size() == 1 && valsSer.size() == 1) {
            match.accept(valsSer.iterator().next(), valsDump.iterator().next());
            valsDump.clear();
            valsSer.clear();
            return;
        }

        final HashMap<ByteBuffer, Integer> frequency = new HashMap<>();
        final Map<ByteBuffer, Set<ByteBuffer>> indexSerialized = indexByObject(
                valsSer.stream().map(k -> Map.entry(k, Objects.requireNonNullElse(getSer.apply(k), new HashSet<>()))),
                frequency
        );
        final Map<ByteBuffer, Set<ByteBuffer>> indexDump = indexByObject(
                valsDump.stream().map(k -> Map.entry(k, Objects.requireNonNullElse(getDump.apply(k), new HashSet<>()))),
                frequency
        );

        ArrayList<Pair<Set<ByteBuffer>, Set<ByteBuffer>>> matchGroups = new ArrayList<>();
        matchGroups.add(Pair.of(new HashSet<>(valsDump), new HashSet<>(valsSer)));
        valsDump.clear();
        valsSer.clear();
        for (ByteBuffer discriminator : frequency.entrySet().stream().filter(x -> x.getValue() > 1).sorted(Comparator.comparing(Map.Entry::getValue)).map(Map.Entry::getKey).collect(Collectors.toList())) {
            final ArrayList<Pair<Set<ByteBuffer>, Set<ByteBuffer>>> newMatchGroups = new ArrayList<>();
            for (Pair<Set<ByteBuffer>, Set<ByteBuffer>> group : matchGroups) {
                final Pair<Set<ByteBuffer>, Set<ByteBuffer>> groupNew = Pair.of(
                        indexDump.get(discriminator),
                        indexSerialized.get(discriminator)
                );
                if (groupNew.getRight() == null || groupNew.getLeft() == null) {
                    newMatchGroups.add(group);
                    continue;
                }
                groupNew.getRight().retainAll(group.getRight());
                groupNew.getLeft().retainAll(group.getLeft());
                group.getRight().removeAll(groupNew.getRight());
                group.getLeft().removeAll(groupNew.getLeft());
                if (groupNew.getLeft().size() == 1 && groupNew.getRight().size() == 1) {
                    match.accept(groupNew.getRight().iterator().next(), groupNew.getLeft().iterator().next());
                } else {
                    newMatchGroups.add(groupNew);
                }
                if (group.getLeft().size() == 1 && group.getRight().size() == 1) {
                    match.accept(group.getRight().iterator().next(), group.getLeft().iterator().next());
                } else {
                    newMatchGroups.add(group);
                }
            }
            matchGroups = newMatchGroups;
            if (matchGroups.size() == 0) {
                break;
            }
        }

        for (Pair<Set<ByteBuffer>, Set<ByteBuffer>> group : matchGroups) {
            valsDump.addAll(group.getLeft());
            valsSer.addAll(group.getRight());
        }
    }

    private static Triple replaceRef(ByteBuffer from, ByteBuffer to, Triple t) {
        if (t.subject.equals(from)) {
            return new Triple(to, t.predicate, t.object);
        }
        if (t.object.equals(from)) {
            return new Triple(t.subject, t.predicate, to);
        }
        return t;
    }

    private static boolean bnodeMatch(ByteBuffer ser, ByteBuffer dump, Parsed pSer, Parsed pDump) {
        final ByteBuffer mapped = serializedToDumpBNode.get(ser);
        if (mapped != null && !mapped.equals(dump)) return false;
        final Set<Triple> replaced = pSer.bnodeTriples.get(ser).stream().map(t -> replaceRef(ser, dump, t)).collect(Collectors.toSet());
        if (!replaced.equals(pDump.bnodeTriples.get(dump))) {
            // BNODE DIFF!
            return false;
        }
        serializedToDumpBNode.put(ser, dump);
        return true;
    }

    private static Set<Triple> matchBNodes(Set<Triple> tDump, Set<Triple> tSer, Parsed pDump, Parsed pSer) {
        final Set<Triple> matched = new HashSet<>();
        for (Triple t : tSer) {
            if (isPrefix(BNODE_PREFIX_UTF8, t.subject)) {
                final Optional<Triple> candidate = tDump.stream().filter(x -> x.predicate.equals(t.predicate) && x.object.equals(t.object)).findAny();
                if (candidate.isPresent() && bnodeMatch(t.subject, candidate.get().subject, pSer, pDump)) {
                    matched.add(candidate.get());
                    continue;
                }
            }
            if (isPrefix(BNODE_PREFIX_UTF8, t.object)) {
                final Optional<Triple> candidate = tDump.stream().filter(x -> x.predicate.equals(t.predicate) && x.subject.equals(t.subject)).findAny();
                if (candidate.isPresent() && bnodeMatch(t.object, candidate.get().object, pSer, pDump)) {
                    matched.add(candidate.get());
                    continue;
                }
            }

            matched.add(t);
        }
        return matched;
    }

    public boolean populate(String entityId, Parsed parsedDump, Parsed parsedSerialized) {
        if (Sets.intersection(parsedDump.otherTriples, parsedSerialized.otherTriples).size() < 1) {
            System.out.println("DESYNC " + "dump " + parsedDump.getId() + " vs serialized " + entityId);
            throw new DesyncException("DESYNC");
        }

        // match value nodes
        final int normalizedOnlyCount = parsedDump.removeNormalizedOnlyValues();
        final BiConsumer<ByteBuffer, ByteBuffer> match = (valSer, valDump) -> {
            for (Map.Entry<ByteBuffer, Triple> link : parsedSerialized.valueNodes.getOrDefault(valSer, new ValueNode()).statementLinks.entrySet()) {
                parsedSerialized.statementTriples.get(link.getKey()).add(new Triple(link.getValue().subject, link.getValue().predicate, valDump));
            }
            for (Map.Entry<ByteBuffer, Triple> link : parsedDump.valueNodes.getOrDefault(valDump, new ValueNode()).statementLinks.entrySet()) {
                parsedDump.statementTriples.get(link.getKey()).add(link.getValue());
            }
            serializedToDumpValue.put(valSer, valDump);
        };

        final Set<ByteBuffer> unmatchedDump = new HashSet<>();
        final Set<ByteBuffer> unmatchedSerialized = new HashSet<>();

        final Map<ByteBuffer, ValueNode> valuesToMatchDump = new HashMap<>(parsedDump.valueNodes);
        final Map<ByteBuffer, ValueNode> valuesToMatchSerialized = new HashMap<>(parsedSerialized.valueNodes);

        for (Iterator<Map.Entry<ByteBuffer, ValueNode>> it = valuesToMatchSerialized.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ByteBuffer, ValueNode> entry = it.next();
            final ByteBuffer dumpValue = serializedToDumpValue.get(entry.getKey());
            if (dumpValue != null) {
                it.remove();
                valuesToMatchDump.remove(dumpValue);
                match.accept(entry.getKey(), dumpValue);
            }
        }

        final Map<ImmutableSet<ByteBuffer>, Set<ByteBuffer>> referrersDump = computeReferrers(valuesToMatchDump);
        final Map<ImmutableSet<ByteBuffer>, Set<ByteBuffer>> referrersSerialized = computeReferrers(valuesToMatchSerialized);


        for (Map.Entry<ImmutableSet<ByteBuffer>, Set<ByteBuffer>> serialized : referrersSerialized.entrySet()) {
            final Set<ByteBuffer> dump = referrersDump.remove(serialized.getKey());
            if (dump == null) {
                unmatchedSerialized.addAll(serialized.getValue());
                continue;
            }

            matchByObjects(dump, serialized.getValue(), k -> parsedDump.valueNodes.get(k).triples, k -> parsedSerialized.valueNodes.get(k).triples, match);
            unmatchedDump.addAll(dump);
            unmatchedSerialized.addAll(serialized.getValue());
        }
        for (Set<ByteBuffer> x : referrersDump.values()) {
            unmatchedDump.addAll(x);
        }
        matchByObjects(unmatchedDump, unmatchedSerialized, k -> parsedDump.valueNodes.get(k).triples, k -> parsedSerialized.valueNodes.get(k).triples, match);

        // compare statements
        int countIdentical = 0;
        int countSameAfterRef = 0;
        for (Iterator<Map.Entry<ByteBuffer, Set<Triple>>> it = parsedSerialized.statementTriples.entrySet().iterator(); it.hasNext(); ) {
            final Map.Entry<ByteBuffer, Set<Triple>> entrySer = it.next();
            final Set<Triple> tDump = parsedDump.statementTriples.remove(entrySer.getKey());
            final Set<Triple> tSer = entrySer.getValue();
            if (tDump == null) {
                continue;
            }
            it.remove();

            if (tDump.equals(tSer)) {
                countIdentical += 1;
                continue;
            }

            // split off all reference statements
            final Set<ByteBuffer> referencesDump = new HashSet<>();
            extractReferences(tDump, referencesDump);
            final Set<ByteBuffer> referencesSer = new HashSet<>();
            extractReferences(tSer, referencesSer);

            final BiConsumer<ByteBuffer, ByteBuffer> matchReference = (refSer, refDump) -> {
                serializedToDumpReference.put(refSer, refDump);

                final Set<Triple> rDump = parsedDump.referenceTriples.get(refDump);
                final Set<Triple> rSer = parsedSerialized.referenceTriples.get(refSer);
                if (rDump == null || rSer == null) return;

                Set<Triple> rSerTrans = rSer.stream().map(t -> {
                    if (t.subject.equals(refSer)) {
                        return new Triple(refDump, t.predicate, t.object);
                    }
                    if (t.object.equals(refSer)) {
                        return new Triple(t.subject, t.predicate, refDump);
                    }
                    return t;
                }).collect(Collectors.toSet());

                if (rSerTrans.equals(rDump)) return;
                rSerTrans = matchBNodes(rDump, rSerTrans, parsedDump, parsedSerialized);
                if (rSerTrans.equals(rDump)) return;

                System.out.println("DIFF IN REFERENCE!");
                System.out.println(new String(bufferBytes(entrySer.getKey())));
                for (Triple t : rDump) {
                    System.out.println(t.toString());
                }
                System.out.println("---");
                for (Triple t : rSer) {
                    System.out.println(t.toString());
                }
                System.exit(5);
            };

            for (Iterator<ByteBuffer> rit = referencesSer.iterator(); rit.hasNext(); ) {
                final ByteBuffer refSer = rit.next();
                final ByteBuffer refDump = serializedToDumpReference.get(refSer);
                if (refDump != null) {
                    matchReference.accept(refSer, refDump);
                    rit.remove();
                }
            }
            matchByObjects(referencesDump, referencesSer, parsedDump.referenceTriples::get, parsedSerialized.referenceTriples::get, matchReference);

            if (tDump.equals(tSer)) {
                countSameAfterRef += 1;
                continue;
            }

            final Set<Triple> tSerTrans = matchBNodes(tDump, tSer, parsedDump, parsedSerialized);
            if (tDump.equals(tSerTrans)) {
                continue;
            }

            System.out.println("REAL DIFF!");
            for (Triple t : tDump) {
                System.out.println(t.toString());
            }
            System.out.println("----");
            for (Triple t : tSer) {
                System.out.println(t.toString());
            }
            System.out.println("----");
            for (Triple t : tSerTrans) {
                System.out.println(t.toString());
            }
        }
        System.out.println("C " + countIdentical + ":" + countSameAfterRef);

        Set<Triple> onlyInSerialized = Sets.difference(parsedSerialized.otherTriples, parsedDump.otherTriples);
        Set<Triple> onlyInDump = Sets.difference(parsedDump.otherTriples, parsedSerialized.otherTriples);
        if (onlyInDump.size() != 0 && onlyInSerialized.size() != 0) {
            System.out.println("DIFF " + entityId);
            for (Triple t : onlyInDump) {
                System.out.println(t.toString());
            }
            System.out.println("----");
            for (Triple t : onlyInSerialized) {
                System.out.println(t.toString());
            }
            System.out.println("ser " + onlyInSerialized.size() + " dump " + onlyInDump.size());
            System.out.println("END");
        } else if (onlyInDump.size() + onlyInSerialized.size() != 0) {
            System.out.println("ONE " + entityId);
        }
        return true;
    }

    private static void extractReferences(Set<Triple> triples, Set<ByteBuffer> references) {
        for (Iterator<Triple> rit = triples.iterator(); rit.hasNext(); ) {
            final Triple t = rit.next();
            if (!t.predicate.equals(ByteBuffer.wrap(WAS_DERIVED_FROM))) continue;
            rit.remove();
            references.add(t.object);
        }
    }
}
