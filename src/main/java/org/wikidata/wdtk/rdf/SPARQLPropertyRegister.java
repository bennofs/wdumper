package org.wikidata.wdtk.rdf;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.wikidata.wdtk.datamodel.helpers.Datamodel;
import org.wikidata.wdtk.datamodel.implementation.PropertyIdValueImpl;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.wikibaseapi.ApiConnection;
import org.wikidata.wdtk.wikibaseapi.apierrors.MediaWikiApiErrorException;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.wikidata.wdtk.datamodel.helpers.Datamodel.SITE_WIKIDATA;

public class SPARQLPropertyRegister extends PropertyRegister {
    /**
     * Constructs a new property register.
     *
     * @param uriPatternPropertyId property id used for a URI Pattern property, e.g., P1921 on
     *                             Wikidata; can be null if no such property should be used
     * @param apiConnection        API connection object that defines how to connect to the
     *                             online API
     * @param siteUri              the URI identifying the site that is accessed (usually the
     *                             prefix of entity URIs), e.g.,
     */
    private SPARQLPropertyRegister(String uriPatternPropertyId, ApiConnection apiConnection, String siteUri) {
        super(uriPatternPropertyId, apiConnection, siteUri);
    }

    public static PropertyRegister createWithWDQS() {
        final SPARQLRepository repository = new SPARQLRepository("https://query.wikidata.org/sparql");
        repository.initialize();
        final RepositoryConnection connection = repository.getConnection();

        TupleQuery query = connection.prepareTupleQuery("SELECT ?prop ?type WHERE { ?prop wikibase:propertyType ?type }");
        final PropertyRegister propertyRegister = new SPARQLPropertyRegister(
                "P1921",
                ApiConnection.getWikidataApiConnection(),
                Datamodel.SITE_WIKIDATA);
        try (final TupleQueryResult result = query.evaluate()) {
            while (result.hasNext()) {
                final BindingSet solution = result.next();
                final IRI property = (IRI) solution.getValue("prop");
                final IRI propType = (IRI) solution.getValue("type");
                // I do not know why this happens, but on 2019-08-16, wikidata query service returns *items* that
                // have a property type ???
                // let's skip them, since that makes no sense
                if (property.getLocalName().startsWith("Q")) continue;

                final PropertyIdValue propId = new PropertyIdValueImpl(property.getLocalName(), SITE_WIKIDATA);
                propertyRegister.setPropertyType(propId, propType.toString());
            }
        } finally {
            repository.shutDown();
        }
        return propertyRegister;
    }

    @Override
    protected void fetchPropertyInformation(PropertyIdValue property) {
        List<String> propertyIds = List.of(property.getId());

        dataFetcher.getFilter().setLanguageFilter(Collections.emptySet());
        dataFetcher.getFilter().setSiteLinkFilter(Collections.emptySet());

        Map<String, EntityDocument> properties;
        try {
            properties = dataFetcher.getEntityDocuments(propertyIds);
        } catch (MediaWikiApiErrorException|IOException e) {
            logger.error("Error when trying to fetch property data: "
                    + e.toString());
            properties = Collections.emptyMap();
        }

        for (Map.Entry<String, EntityDocument> entry : properties.entrySet()) {
            EntityDocument propertyDocument = entry.getValue();
            if (!(propertyDocument instanceof PropertyDocument)) {
                continue;
            }

            String datatype = ((PropertyDocument) propertyDocument)
                    .getDatatype().getIri();
            this.datatypes.put(entry.getKey(), datatype);
            logger.info("Fetched type information for property "
                    + entry.getKey() + " online: " + datatype);

            if (!DatatypeIdValue.DT_STRING.equals(datatype)) {
                continue;
            }

            for (StatementGroup sg : ((PropertyDocument) propertyDocument)
                    .getStatementGroups()) {
                if (!sg.getProperty().getId().equals(this.uriPatternPropertyId)) {
                    continue;
                }
                for (Statement statement : sg) {
                    if (statement.getMainSnak() instanceof ValueSnak
                            && statement.getValue() instanceof StringValue) {
                        String uriPattern = ((StringValue) statement.getValue()).getString();
                        if (this.uriPatterns.containsKey(entry.getKey())) {
                            logger.info("Found multiple URI patterns for property "
                                    + entry.getKey()
                                    + " but only one is supported in current code.");
                        }
                        this.uriPatterns.put(entry.getKey(), uriPattern);
                    }
                }
            }
        }

        if (!this.datatypes.containsKey(property.getId())) {
            logger.error("Failed to fetch type information for property "
                    + property.getId() + " online. Assuming string.");
            this.setPropertyType(property, "http://wikiba.se/ontology#String");
        }
    }

}
