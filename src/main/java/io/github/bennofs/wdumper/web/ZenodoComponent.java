package io.github.bennofs.wdumper.web;

import com.google.inject.Inject;
import io.github.bennofs.wdumper.jooq.enums.DB_ZenodoTarget;
import io.github.bennofs.wdumper.jooq.tables.records.DB_DumpRecord;
import io.github.bennofs.wdumper.jooq.tables.records.DB_ZenodoRecord;
import io.github.bennofs.wdumper.model.Zenodo;
import io.github.bennofs.wdumper.zenodo.*;
import org.jooq.DSLContext;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.jackson.Jackson;
import ratpack.server.PublicAddress;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

import static io.github.bennofs.wdumper.jooq.Tables.DUMP;
import static io.github.bennofs.wdumper.jooq.Tables.ZENODO;

public class ZenodoComponent implements Action<Chain> {
    final DSLContext db;
    final ZenodoApiProvider apiProvider;

    @Inject
    public ZenodoComponent(DSLContext db, ZenodoApiProvider apiProvider) {
        Objects.requireNonNull(db);
        Objects.requireNonNull(apiProvider);

        this.db = db;
        this.apiProvider = apiProvider;
    }

    public static class ZenodoRequest {
        public int id;
        public Zenodo.Target target;
    }

    private String buildZenodoDescription(Context ctx, DB_DumpRecord dump) {
        final PublicAddress addr = ctx.get(PublicAddress.class);
        return String.format("<p>RDF dump of wikidata produced with <a href=\"%s\">wdumper</a>.</p>" +
                "<p>%s<br><a href=\"%s\">View on wdumper</a></p>" +
                "<p><b>entity count<b>: %d, <b>statement count</b>: %d, <b>triple count</b>: %d</p>",
                addr.get().toASCIIString(),
                dump.getDescription(),
                addr.builder().segment("dump").segment("%d", dump.getId()),
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

    private void post(Context ctx, ZenodoRequest request) throws ZenodoApiException, IOException {
        final DB_DumpRecord dump = db.fetchOne(DUMP, DUMP.ID.eq(request.id));
        if (dump == null) {
            ctx.notFound();
            return;
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
                .description(buildZenodoDescription(ctx, dump))
                .uploadType("dataset")
                .build();
        deposit = api.updateDeposit(deposit);

        final int zenodoId = db.insertInto(ZENODO)
                .columns(ZENODO.DEPOSIT_ID, ZENODO.DUMP_ID, ZENODO.DOI, ZENODO.TARGET)
                .values(deposit.id, dump.getId(), deposit.metadata.doi(), targetToDbTarget(request.target))
                .returning(ZENODO.ID)
                .execute();

        final PublicAddress publicAddress = ctx.get(PublicAddress.class);
        ctx.redirect(201, publicAddress.builder().path("api/zenodo").segment("%d", zenodoId));
    }

    private void get(Context ctx, int id) {
        final DB_ZenodoRecord record = db.fetchOne(ZENODO, ZENODO.ID.eq(id));
        if (record == null) {
            ctx.notFound();
        } else {
            ctx.render(Jackson.json(record));
        }
    }

    @Override
    public void execute(Chain chain) throws Exception {
        chain.post("zenodo", ctx -> {
            ctx.parse(Jackson.fromJson(ZenodoRequest.class)).then(r -> this.post(ctx, r));
        }).get("zenodo/:id", ctx -> {
            try {
                this.get(ctx, Integer.parseInt(ctx.getPathTokens().get("id")));
            } catch (NumberFormatException e) {
                ctx.notFound();
            }
        });
    }
}
