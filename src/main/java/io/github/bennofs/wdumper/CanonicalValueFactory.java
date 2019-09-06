package io.github.bennofs.wdumper;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.AbstractValueFactory;
import org.eclipse.rdf4j.model.vocabulary.GEO;
import org.eclipse.rdf4j.model.vocabulary.XMLSchema;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CanonicalValueFactory extends AbstractValueFactory {
    private static final CanonicalValueFactory instance;
    static {
        instance = new CanonicalValueFactory();
    }

    public static ValueFactory getInstance() {
        return instance;
    }

    @Override
    public IRI createIRI(String iri) {
        if(iri.contains("http://commons.wikimedia.org/wiki/Special:FilePath")) {
            iri = iri.replace("%20", "_");
        }
        try {
            URI url = new URI(iri);
            final URI canonicalize = new URI(url.getScheme(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getFragment());
            iri = canonicalize.toString();
        } catch(URISyntaxException ignored) {

        }
        return super.createIRI(iri);
    }

    @Override
    public Literal createLiteral(String value, IRI datatype) {
        if (datatype.equals(XMLSchema.DECIMAL)) {
            if (value.startsWith("+")) {
                value = value.substring(1);
            }
        }
        if (datatype.equals(GEO.WKT_LITERAL)) {
            final Pattern p = Pattern.compile("Point\\(([^ ]*) ([^ ]*)\\)");
            final Matcher m = p.matcher(value);

            if (m.matches()) {
                final MatchResult result = m.toMatchResult();

                float f1 = Float.parseFloat(result.group(1));
                float f2 = Float.parseFloat(result.group(2));
                value = "Point(" + Math.round(f1) + " " + Math.round(f2) + ")";
            }
        }
        return super.createLiteral(value, datatype);
    }
}
