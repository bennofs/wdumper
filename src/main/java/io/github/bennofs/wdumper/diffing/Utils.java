package io.github.bennofs.wdumper.diffing;

import org.eclipse.rdf4j.common.io.ByteArrayUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Utils {
    static final Map<ByteBuffer, Byte> urlEscapeDecode = new HashMap<>();
    static final byte[] ENTITY_DATA_UTF8;
    static final byte[] DIRECT_NORMALIZED_UTF8;
    static final byte[] OWL_UTF8;
    static final byte[] OWL_DATATYPE_UTF8;
    static final byte[] OWL_OBJECT_UTF8;
    static final byte[] OWL_CLASS_UTF8;
    static final byte[] OWL_RESTRICTION_UTF8;
    static final byte[] OWL_THING_UTF8;
    static final byte[] OWL_COMPLEMENT_UTF8;
    static final byte[] OWL_ON_PROPERTY_UTF8;
    static final byte[] WIKIBASE_DUMP;
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
    static final byte[] QUANTITY_NORMALIZED;
    static final byte[] GEO_AUTO_PRECISION;
    static final Pattern POINT_PATTERN = Pattern.compile("(<[^>]*> )?Point\\(([^ ]*) ([^ ]*)\\)");
    public static final ByteBuffer SUBJECT_SELF = ByteBuffer.wrap("_:_".getBytes(StandardCharsets.UTF_8));

    static {
        ENTITY_DATA_UTF8 = "<https://www.wikidata.org/wiki/Special:EntityData/".getBytes(StandardCharsets.UTF_8);
        DIRECT_NORMALIZED_UTF8 = "<http://www.wikidata.org/prop/direct-normalized".getBytes(StandardCharsets.UTF_8);
        OWL_UTF8 = "<http://www.w3.org/2002/07/owl#".getBytes(StandardCharsets.UTF_8);
        OWL_DATATYPE_UTF8 = "<http://www.w3.org/2002/07/owl#DatatypeProperty>".getBytes(StandardCharsets.UTF_8);
        OWL_OBJECT_UTF8 = "<http://www.w3.org/2002/07/owl#ObjectProperty>".getBytes(StandardCharsets.UTF_8);
        OWL_CLASS_UTF8 = "<http://www.w3.org/2002/07/owl#Class>".getBytes(StandardCharsets.UTF_8);
        OWL_THING_UTF8 = "<http://www.w3.org/2002/07/owl#Thing>".getBytes(StandardCharsets.UTF_8);
        OWL_RESTRICTION_UTF8 = "<http://www.w3.org/2002/07/owl#Restriction>".getBytes(StandardCharsets.UTF_8);
        OWL_COMPLEMENT_UTF8 = "<http://www.w3.org/2002/07/owl#complementOf>".getBytes(StandardCharsets.UTF_8);
        OWL_ON_PROPERTY_UTF8 = "<http://www.w3.org/2002/07/owl#onProperty>".getBytes(StandardCharsets.UTF_8);
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
        QUANTITY_NORMALIZED = "<http://wikiba.se/ontology#quantityNormalized>".getBytes(StandardCharsets.UTF_8);
        GEO_AUTO_PRECISION = "<http://wikiba.se/ontology#GeoAutoPrecision>".getBytes(StandardCharsets.UTF_8);
        WIKIBASE_DUMP = "<http://wikiba.se/ontology#Dump>".getBytes(StandardCharsets.UTF_8);
    }

    static {
        // these are not handled by standard url normalization (maybe they are invalid?)
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%28"), StandardCharsets.US_ASCII.encode("(").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%29"), StandardCharsets.US_ASCII.encode(")").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%2C"), StandardCharsets.US_ASCII.encode(",").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%21"), StandardCharsets.US_ASCII.encode("!").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%7E"), StandardCharsets.US_ASCII.encode("~").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%3B"), StandardCharsets.US_ASCII.encode(";").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%7B"), StandardCharsets.US_ASCII.encode("{").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%7C"), StandardCharsets.US_ASCII.encode("|").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%7D"), StandardCharsets.US_ASCII.encode("}").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%60"), StandardCharsets.US_ASCII.encode("`").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%5E"), StandardCharsets.US_ASCII.encode("^").get());

        // these are standard
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%3A"), StandardCharsets.US_ASCII.encode(":").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%40"), StandardCharsets.US_ASCII.encode("@").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%5C"), StandardCharsets.US_ASCII.encode("\\").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%2A"), StandardCharsets.US_ASCII.encode("*").get());
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%24"), StandardCharsets.US_ASCII.encode("$").get());

        // this one is weird
        urlEscapeDecode.put(StandardCharsets.US_ASCII.encode("%2F"), StandardCharsets.US_ASCII.encode("/").get());
    }



    public static String bufferString(ByteBuffer b) {
        return new String(bufferBytes(b), StandardCharsets.UTF_8);
    }

    public static byte[] bufferBytes(ByteBuffer b)  {
        final byte[] out = new byte[b.remaining()];
        b.mark();
        b.get(out);
        b.reset();
        return out;
    }

    static void replaceUrlEscapes(ByteBuffer buf) {
        final int d = 2;
        int prevIdx = buf.position();
        while (true) {
            int idx = ByteArrayUtil.find(buf.array(), prevIdx, buf.limit(), (byte)0x25);
            if (idx == -1 || idx + 2 >= buf.limit()) return;
            final ByteBuffer slice = buf.slice();
            slice.position(idx - buf.position());
            slice.limit(slice.position() + 3);
            final Byte replacement = urlEscapeDecode.get(slice);
            if (replacement == null) {
                prevIdx = idx + 2;
                continue;
            }

            slice.put(replacement);
            if (replacement == 0x5c) {
                slice.put((byte)0x5c);
                System.arraycopy(buf.array(), idx + 3, buf.array(), idx + 2, buf.limit() - idx - 3);
                buf.limit(buf.limit() - 1);
                prevIdx = idx + 2;
            } else {
                System.arraycopy(buf.array(), idx + 3, buf.array(), idx + 1, buf.limit() - idx - 3);
                buf.limit(buf.limit() - 2);
                prevIdx = idx + 1;
            }
        }
    }

    static boolean isPrefix(byte[] prefix, ByteBuffer buf) {
        if (buf.remaining() < prefix.length) return false;
        buf.mark();
        byte[] data = new byte[prefix.length];
        buf.get(data);
        buf.reset();
        return Arrays.equals(prefix, 0, prefix.length, data, 0, data.length);
    }

    public static void assumption(boolean b, String s) {
        if (!b) {
            throw new RuntimeException("assumption violated: " + s);
        }
    }
}
