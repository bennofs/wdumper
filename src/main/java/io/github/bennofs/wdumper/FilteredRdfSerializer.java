package io.github.bennofs.wdumper;

import io.github.bennofs.wdumper.spec.DumpSpec;
import org.apache.commons.lang3.NotImplementedException;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.rdf.*;
import org.wikidata.wdtk.rdf.values.AnyValueConverter;

import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class FilteredRdfSerializer implements EntityDocumentDumpProcessor {
    private final static Logger logger = LoggerFactory.getLogger(FilteredRdfSerializer.class);

    private final DumpSpec spec;
    private final RdfWriter rdfWriter;
    private final Sites sites;
    private final PropertyRegister propertyRegister;

    private final SnakRdfConverter snakRdfConverter;
    private final OwlDeclarationBuffer owlDeclarationBuffer = new OwlDeclarationBuffer();
    private final ReferenceRdfConverter referenceRdfConverter;
    private final RankBuffer rankBuffer = new RankBuffer();
    private int count = 0;

    FilteredRdfSerializer(DumpSpec spec, OutputStream output, Sites sites, PropertyRegister propertyRegister) {
        this.spec = spec;
        this.rdfWriter = new RdfWriter(spec.getFormat(), output);
        this.sites = sites;
        this.propertyRegister = propertyRegister;

        final AnyValueConverter valueRdfConverter = new AnyValueConverter(rdfWriter,
                this.owlDeclarationBuffer, this.propertyRegister);
        this.snakRdfConverter = new SnakRdfConverter(rdfWriter,
                this.owlDeclarationBuffer, this.propertyRegister,
                valueRdfConverter);
        this.referenceRdfConverter = new ReferenceRdfConverter(rdfWriter,
                this.snakRdfConverter, this.propertyRegister.getUriPrefix());
    }


    @Override
    public void processItemDocument(ItemDocument itemDocument) {
        this.count += 1;
        try {
            if (!this.spec.includeDocument(itemDocument)) return;

            writeItemDocument(itemDocument);
        } catch(Exception e) {
            System.err.println("failed to process item, document " + this.count);
            throw e;
        }
    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
        this.count += 1;
        try {
            if (!this.spec.includeDocument(propertyDocument)) return;

            writePropertyDocument(propertyDocument);
        } catch(Exception e) {
            System.err.println("failed to process property, document " + this.count);
            throw e;
        }
    }

    @Override
    public void processLexemeDocument(LexemeDocument lexemeDocument) {
        this.count += 1;
        try {
            if (!this.spec.includeDocument(lexemeDocument)) return;

            throw new NotImplementedException("serialization of lexemes not implemented yet");
        } catch(Exception e) {
            System.err.println("failed to process lexeme, document " + this.count);
            throw e;
        }
    }


    /**
     * Writes OWL declarations for all basic vocabulary elements used in the
     * dump.
     *
     * @throws RDFHandlerException
     */
    public void writeBasicDeclarations() throws RDFHandlerException {
        for (Map.Entry<String, String> uriType : Vocabulary
                .getKnownVocabularyTypes().entrySet()) {
            this.rdfWriter.writeTripleUriObject(uriType.getKey(),
                    RdfWriter.RDF_TYPE, uriType.getValue());
        }
    }

    public void writeNamespaceDeclarations() throws RDFHandlerException {
        this.rdfWriter.writeNamespaceDeclaration("wd",
                this.propertyRegister.getUriPrefix());
        this.rdfWriter
                .writeNamespaceDeclaration("wikibase", Vocabulary.PREFIX_WBONTO);
        this.rdfWriter.writeNamespaceDeclaration("rdf", Vocabulary.PREFIX_RDF);
        this.rdfWriter
                .writeNamespaceDeclaration("rdfs", Vocabulary.PREFIX_RDFS);
        this.rdfWriter.writeNamespaceDeclaration("owl", Vocabulary.PREFIX_OWL);
        this.rdfWriter.writeNamespaceDeclaration("xsd", Vocabulary.PREFIX_XSD);
        this.rdfWriter.writeNamespaceDeclaration("schema",
                Vocabulary.PREFIX_SCHEMA);
        this.rdfWriter
                .writeNamespaceDeclaration("skos", Vocabulary.PREFIX_SKOS);
        this.rdfWriter
                .writeNamespaceDeclaration("prov", Vocabulary.PREFIX_PROV);
    }

    public void writeItemDocument(ItemDocument document)
            throws RDFHandlerException {

        String subjectUri = document.getEntityId().getIri();
        Resource subject = this.rdfWriter.getUri(subjectUri);

        if (this.spec.isMeta()) { // TODO: not sure if meta is the right name for this option
            this.rdfWriter.writeTripleValueObject(subject, RdfWriter.RDF_TYPE,
                    RdfWriter.WB_ITEM);
        }

        writeDocumentTerms(subject, document);
        writeTruthyStatements(subject, document);
        if (!spec.isTruthy())
            writeStatements(subject, document);

        if (spec.isSitelinks())
            writeSiteLinks(subject, document.getSiteLinks());

        this.snakRdfConverter.writeAuxiliaryTriples();
        this.owlDeclarationBuffer.writePropertyDeclarations(this.rdfWriter, !spec.isTruthy(), true);
        this.referenceRdfConverter.writeReferences();
    }

    public void writePropertyDocument(PropertyDocument document)
            throws RDFHandlerException {

        propertyRegister.setPropertyType(document.getEntityId(), document
                .getDatatype().getIri());

        String propertyUri = document.getEntityId().getIri();
        Resource subject = this.rdfWriter.getUri(propertyUri);

        this.rdfWriter.writeTripleValueObject(subject, RdfWriter.RDF_TYPE,
                RdfWriter.WB_PROPERTY);

        writeDocumentTerms(subject, document);

        this.rdfWriter.writeTripleValueObject(subject,
                RdfWriter.WB_PROPERTY_TYPE,
                this.rdfWriter.getUri(document.getDatatype().getIri()));

        writeStatements(subject, document);
        writeInterPropertyLinks(document);

        this.snakRdfConverter.writeAuxiliaryTriples();
        this.owlDeclarationBuffer.writePropertyDeclarations(this.rdfWriter, true, true);
        this.referenceRdfConverter.writeReferences();
    }

    /**
     * Writes triples which conect properties with there corresponding rdf
     * properties for statements, simple statements, qualifiers, reference
     * attributes and values.
     *
     * @param document
     * @throws RDFHandlerException
     */
    void writeInterPropertyLinks(PropertyDocument document)
            throws RDFHandlerException {
        Resource subject = this.rdfWriter.getUri(document.getEntityId()
                .getIri());
        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_DIRECT_CLAIM_PROP), Vocabulary
                .getPropertyUri(document.getEntityId(),
                        PropertyContext.DIRECT));

        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_CLAIM_PROP), Vocabulary.getPropertyUri(
                document.getEntityId(), PropertyContext.STATEMENT));

        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_STATEMENT_PROP), Vocabulary
                .getPropertyUri(document.getEntityId(),
                        PropertyContext.VALUE_SIMPLE));

        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                        .getUri(Vocabulary.WB_STATEMENT_VALUE_PROP),
                Vocabulary.getPropertyUri(document.getEntityId(),
                        PropertyContext.VALUE));

        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_QUALIFIER_PROP), Vocabulary
                .getPropertyUri(document.getEntityId(),
                        PropertyContext.QUALIFIER_SIMPLE));

        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_QUALIFIER_VALUE_PROP), Vocabulary
                .getPropertyUri(document.getEntityId(),
                        PropertyContext.QUALIFIER));

        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_REFERENCE_PROP), Vocabulary
                .getPropertyUri(document.getEntityId(),
                        PropertyContext.REFERENCE_SIMPLE));

        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_REFERENCE_VALUE_PROP), Vocabulary
                .getPropertyUri(document.getEntityId(),
                        PropertyContext.REFERENCE));

        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_NO_VALUE_PROP), Vocabulary
                .getPropertyUri(document.getEntityId(),
                        PropertyContext.NO_VALUE));
        this.rdfWriter.writeTripleUriObject(subject, this.rdfWriter
                .getUri(Vocabulary.WB_NO_QUALIFIER_VALUE_PROP), Vocabulary
                .getPropertyUri(document.getEntityId(),
                        PropertyContext.NO_QUALIFIER_VALUE));
        // TODO something more with NO_VALUE
    }

    void writeStatements(Resource subject, StatementDocument statementDocument)
            throws RDFHandlerException {
        for (StatementGroup statementGroup : statementDocument
                .getStatementGroups()) {
            IRI property = this.rdfWriter.getUri(Vocabulary.getPropertyUri(
                    statementGroup.getProperty(), PropertyContext.STATEMENT));
            for (Statement statement : statementGroup) {
                this.rdfWriter.writeTripleUriObject(subject, property,
                        Vocabulary.getStatementUri(statement));
            }
        }

        for (StatementGroup statementGroup : statementDocument
                .getStatementGroups()) {
            if (!spec.includeProperty(statementGroup.getProperty().getId())) continue;

            for (Statement statement : statementGroup) {
                writeStatement(statement);
            }
            writeBestRankTriples();
        }
    }

    void writeTruthyStatements(Resource subject,
                               StatementDocument statementDocument) {
        for (StatementGroup statementGroup : statementDocument
                .getStatementGroups()) {
            if (!spec.includeProperty(statementGroup.getProperty().getId())) continue;

            final StatementGroup best = statementGroup.getBestStatements();
            if (best == null) continue;

            for (Statement statement : best) {
                if (statement.getQualifiers().size() == 0) {
                    this.snakRdfConverter.setSnakContext(subject,
                            PropertyContext.DIRECT);
                    statement.getMainSnak()
                            .accept(this.snakRdfConverter);
                }
            }
        }
    }

    void writeDocumentTerms(Resource subject, TermedDocument document)
            throws RDFHandlerException {
        writeTermTriples(subject, RdfWriter.RDFS_LABEL, document.getLabels().values()); // TODO: what about schema:name,skos:prefLabel
        writeTermTriples(subject, RdfWriter.SCHEMA_DESCRIPTION, document.getDescriptions().values());
        for (List<MonolingualTextValue> aliases : document.getAliases().values()) {
            writeTermTriples(subject, RdfWriter.SKOS_ALT_LABEL, aliases);
        }
    }

    void writeTermTriples(Resource subject, IRI predicate,
                          Collection<MonolingualTextValue> terms) throws RDFHandlerException {
        for (MonolingualTextValue mtv : terms) {
            if (!spec.includeLanguage(mtv.getLanguageCode())) continue;

            this.rdfWriter.writeTripleValueObject(subject, predicate,
                    RdfConverter.getMonolingualTextValueLiteral(mtv,
                            this.rdfWriter));
        }
    }

    /**
     * Writes a triple for the {@link StatementRank} of a {@link Statement} to
     * the dump.
     *
     * @param subject
     * @param rank
     */
     void writeStatementRankTriple(Resource subject, StatementRank rank) {
        try {
            this.rdfWriter.writeTripleUriObject(subject, RdfWriter.WB_RANK,
                    getUriStringForRank(rank));
            this.rankBuffer.add(rank, subject);

        } catch (RDFHandlerException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Writes triples to determine the statements with the highest rank.
     */
    void writeBestRankTriples() {
        for (Resource resource : this.rankBuffer.getBestRankedStatements()) {
            try {
                this.rdfWriter.writeTripleUriObject(resource,
                        RdfWriter.RDF_TYPE, RdfWriter.WB_BEST_RANK.toString());
            } catch (RDFHandlerException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        this.rankBuffer.clear();
    }

    void writeStatement(Statement statement) throws RDFHandlerException {
        String statementUri = Vocabulary.getStatementUri(statement);
        Resource statementResource = this.rdfWriter.getUri(statementUri);

        this.rdfWriter.writeTripleValueObject(statementResource,
                RdfWriter.RDF_TYPE, RdfWriter.WB_STATEMENT);
        writeClaim(statementResource, statement.getClaim());

        if (this.spec.isReferences())
            writeReferences(statementResource, statement.getReferences());

        writeStatementRankTriple(statementResource, statement.getRank());

    }

    void writeReferences(Resource statementResource,
                         List<? extends Reference> references) throws RDFHandlerException {
        for (Reference reference : references) {
            Resource resource = this.referenceRdfConverter.addReference(reference);
            this.rdfWriter.writeTripleValueObject(statementResource,
                    RdfWriter.PROV_WAS_DERIVED_FROM, resource);
        }
    }

    void writeClaim(Resource claimResource, Claim claim) {
        // write main snak
        this.snakRdfConverter.setSnakContext(claimResource,
                PropertyContext.VALUE);
        claim.getMainSnak().accept(this.snakRdfConverter);
        this.snakRdfConverter.setSnakContext(claimResource,
                PropertyContext.VALUE_SIMPLE);
        claim.getMainSnak().accept(this.snakRdfConverter);

        if (this.spec.isQualifiers()) {
            // write qualifier
            this.snakRdfConverter.setSnakContext(claimResource,
                    PropertyContext.QUALIFIER);
            for (SnakGroup snakGroup : claim.getQualifiers()) {
                for (Snak snak : snakGroup) {
                    snak.accept(this.snakRdfConverter);
                }
            }
            this.snakRdfConverter.setSnakContext(claimResource,
                    PropertyContext.QUALIFIER_SIMPLE);
            for (SnakGroup snakGroup : claim.getQualifiers()) {
                for (Snak snak : snakGroup) {
                    snak.accept(this.snakRdfConverter);
                }
            }
        }
    }

    void writeSiteLinks(Resource subject, Map<String, SiteLink> siteLinks)
            throws RDFHandlerException {
        for (String key : siteLinks.keySet()) {
            SiteLink siteLink = siteLinks.get(key);
            String siteLinkUrl = this.sites.getSiteLinkUrl(siteLink);
            if (siteLinkUrl != null) {
                IRI siteLinkUri = this.rdfWriter.getUri(siteLinkUrl);

                this.rdfWriter.writeTripleValueObject(siteLinkUri,
                        RdfWriter.RDF_TYPE, RdfWriter.SCHEMA_ARTICLE);
                this.rdfWriter.writeTripleValueObject(siteLinkUri,
                        RdfWriter.SCHEMA_ABOUT, subject);

                String siteLanguageCode = this.sites.getLanguageCode(siteLink.getSiteKey());
                this.rdfWriter.writeTripleStringObject(siteLinkUri,
                        RdfWriter.SCHEMA_IN_LANGUAGE, convertSiteLanguageCode(siteLanguageCode));

                for(ItemIdValue badge : siteLink.getBadges()) {
                    this.rdfWriter.writeTripleUriObject(siteLinkUri,
                            RdfWriter.WB_BADGE, badge.getIri());
                }
            } else {
                logger.warn("Failed to find URL for page \""
                        + siteLink.getPageTitle() + "\" on site \""
                        + siteLink.getSiteKey() + "\"");
            }
        }
    }

    private String convertSiteLanguageCode(String languageCode) {
        try {
            return WikimediaLanguageCodes.getLanguageCode(languageCode);
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown Wikimedia language code \""
                    + languageCode
                    + "\". Using this code in RDF now, but this might be wrong.");
            return languageCode;
        }
    }

    /**
     *
     * @param value
     * @return
     */
    public static org.eclipse.rdf4j.model.Value getMonolingualTextValueLiteral(
            MonolingualTextValue value, RdfWriter rdfWriter) {
        String languageCode;
        try {
            languageCode = WikimediaLanguageCodes.getLanguageCode(value
                    .getLanguageCode());
        } catch (IllegalArgumentException e) {
            languageCode = value.getLanguageCode();
            logger.warn("Unknown Wikimedia language code \""
                    + languageCode
                    + "\". Using this code in RDF now, but this might be wrong.");
        }
        return rdfWriter.getLiteral(value.getText(), languageCode);
    }

    /**
     * Returns an URI which represents the statement rank in a triple.
     *
     * @param rank
     * @return
     */
    String getUriStringForRank(StatementRank rank) {
        switch (rank) {
            case NORMAL:
                return Vocabulary.WB_NORMAL_RANK;
            case PREFERRED:
                return Vocabulary.WB_PREFERRED_RANK;
            case DEPRECATED:
                return Vocabulary.WB_DEPRECATED_RANK;
            default:
                throw new IllegalArgumentException();
        }
    }


    @Override
    public void open() {
        this.rdfWriter.start();
        writeNamespaceDeclarations();
        writeBasicDeclarations();
    }

    @Override
    public void close() {
        this.rdfWriter.finish();
    }
}
