package io.github.bennofs.wdumper.api;

import com.google.inject.Inject;
import io.github.bennofs.wdumper.jooq.enums.ZenodoTarget;
import io.github.bennofs.wdumper.jooq.tables.records.DumpRecord;
import io.github.bennofs.wdumper.jooq.tables.records.ZenodoRecord;
import io.github.bennofs.wdumper.zenodo.*;
import org.jooq.DSLContext;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.jackson.Jackson;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

import static io.github.bennofs.wdumper.jooq.Tables.DUMP;
import static io.github.bennofs.wdumper.jooq.Tables.ZENODO;

public class ZenodoComponent implements Action<Chain> {
    final DSLContext db;
    final ApiConfiguration configuration;
    final ZenodoApiProvider apiProvider;

    @Inject
    public ZenodoComponent(ApiConfiguration configuration, DSLContext db, ZenodoApiProvider apiProvider) {
        Objects.requireNonNull(configuration);
        Objects.requireNonNull(db);
        Objects.requireNonNull(apiProvider);

        this.db = db;
        this.configuration = configuration;
        this.apiProvider = apiProvider;
    }

    public static class ZenodoRequest {
        public int id;
        public ZenodoTarget target;
    }

    private String buildZenodoDescription(DumpRecord dump) {
        return String.format("<p>RDF dump of wikidata produced with <a href=\"%s\">wdumper</a>.</p>" +
                "<p>%s<br><a href=\"%s\">View on wdumper</a></p>" +
                "<p><b>entity count<b>: %d, <b>statement count</b>: %d, <b>triple count</b>: %d</p>",
                configuration.publicAddress.toASCIIString(),
                dump.getDescription(),
                configuration.publicAddress.resolve("dump/" + dump.getId()),
                dump.getEntityCount(),
                dump.getStatementCount(),
                dump.getTripleCount()
        );
    }

    private void post(Context ctx, ZenodoRequest request) throws ZenodoApiException, IOException {
        final DumpRecord dump = db.fetchOne(DUMP, DUMP.ID.eq(request.id));
        if (dump == null) {
            ctx.notFound();
            return;
        }

        final ZenodoApi api = apiProvider.getZenodoApiFor(request.target);
        if (api == null) {
            throw new RuntimeException("zenodo api target " + request.target + " is not configured");
        }

        final Deposit deposit = api.createDeposit();
        deposit.metadata = deposit.metadata.toBuilder()
                .creators(Collections.singletonList(Creator.builder().name("Benno Fünfstück").build()))
                .accessRight("open")
                .license("cc-zero")
                .description(buildZenodoDescription(dump))
                .uploadType("dataset")
                .build();
        api.updateDeposit(deposit);

        final int zenodoId = db.insertInto(ZENODO)
                .columns(ZENODO.DEPOSIT_ID, ZENODO.DUMP_ID, ZENODO.DOI, ZENODO.TARGET)
                .values(deposit.id, dump.getId(), deposit.metadata.doi(), request.target)
                .returning(ZENODO.ID)
                .execute();

        ctx.redirect(201, configuration.apiRoot + "/zenodo/" + zenodoId);
    }

    private void get(Context ctx, int id) {
        final ZenodoRecord record = db.fetchOne(ZENODO, ZENODO.ID.eq(id));
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
