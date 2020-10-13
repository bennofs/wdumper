package io.github.bennofs.wdumper.zenodo;

import io.github.bennofs.wdumper.jooq.enums.DB_ZenodoTarget;
import io.github.bennofs.wdumper.jooq.tables.records.DB_DumpRecord;
import io.github.bennofs.wdumper.jooq.tables.records.DB_ZenodoRecord;
import io.github.bennofs.wdumper.model.Zenodo;
import io.github.bennofs.wdumper.templating.UrlBuilder;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;

import static io.github.bennofs.wdumper.jooq.Tables.DUMP;
import static io.github.bennofs.wdumper.jooq.Tables.ZENODO;


@Path("zenodo")
public class ZenodoResource {
    private final DSLContext db;
    private final UrlBuilder urlBuilder;
    private final ZenodoApiProvider apiProvider;

    @Inject
    public ZenodoResource(DSLContext db, UrlBuilder urlBuilder, ZenodoApiProvider apiProvider) {
        this.db = db;
        this.urlBuilder = urlBuilder;
        this.apiProvider = apiProvider;
    }

    public static class ZenodoRequest {
        public int id;
        public Zenodo.Target target;
    }

    private String buildZenodoDescription(DB_DumpRecord dump) {
        return String.format("<p>RDF dump of wikidata produced with <a href=\"%s\">wdumper</a>.</p>" +
                        "<p>%s<br><a href=\"%s\">View on wdumper</a></p>" +
                        "<p><b>entity count<b>: %d, <b>statement count</b>: %d, <b>triple count</b>: %d</p>",
                urlBuilder.urlPathString(""),
                dump.getDescription(),
                urlBuilder.urlPathString(String.format("dump/%d", dump.getId())),
                dump.getEntityCount(),
                dump.getStatementCount(),
                dump.getTripleCount()
        );
    }

    private static DB_ZenodoTarget targetToDbTarget(Zenodo.Target target) {
        switch(target) {
            case SANDBOX:
                return DB_ZenodoTarget.SANDBOX;
            case RELEASE:
                return DB_ZenodoTarget.RELEASE;
            default:
                throw new IllegalArgumentException("invalid zenodo target " + target);
        }
    }

    @POST
    @Consumes(value = {"application/json"})
    public Response post(ZenodoRequest request) throws IOException, ZenodoApiException {
        final DB_DumpRecord dump = db.fetchOne(DUMP, DUMP.ID.eq(request.id));
        if (dump == null) {
            throw new NotFoundException(String.format("there is no dump with id %d", request.id));
        }

        final ZenodoApi api = apiProvider.getZenodoApiFor(request.target);
        if (api == null) {
            throw new RuntimeException("zenodo api target " + request.target + " is not configured");
        }

        Deposit deposit = api.createDeposit();
        deposit.metadata = deposit.metadata.toBuilder()
                .title(("Wikidata Dump " + dump.getTitle()).stripTrailing())
                .creators(Collections.singletonList(Creator.builder().name("Benno Fünfstück").build()))
                .accessRight("open")
                .license("cc-zero")
                .description(buildZenodoDescription(dump))
                .uploadType("dataset")
                .build();
        deposit = api.updateDeposit(deposit);

        final int zenodoId = db.insertInto(ZENODO)
                .columns(ZENODO.DEPOSIT_ID, ZENODO.DUMP_ID, ZENODO.DOI, ZENODO.TARGET)
                .values(deposit.id, dump.getId(), deposit.metadata.doi(), targetToDbTarget(request.target))
                .returning(ZENODO.ID)
                .execute();

        return Response.created(urlBuilder.urlPath(String.format("zenodo/%d", zenodoId))).build();
    }

    @GET
    @Produces(value = {"application/json"})
    @Path("{id}")
    public DB_ZenodoRecord getJson(@PathParam("id") int id) {
        final DB_ZenodoRecord record = db.fetchOne(ZENODO, ZENODO.ID.eq(id));
        if (record == null) {
            throw new NotFoundException(String.format("there is no zenodo dump record with id %d", id));
        }

        return record;
    }
}
