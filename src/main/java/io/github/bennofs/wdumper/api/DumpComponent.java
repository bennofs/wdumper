package io.github.bennofs.wdumper.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.Inject;
import io.github.bennofs.wdumper.jooq.tables.pojos.Dump;
import io.github.bennofs.wdumper.jooq.tables.records.DumpRecord;
import io.github.bennofs.wdumper.spec.DumpSpecJson;
import org.jooq.DSLContext;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.jackson.Jackson;

import static io.github.bennofs.wdumper.jooq.Tables.DUMP;

/**
 * This component provides create and query operations for dumps.
 */
public class DumpComponent implements Action<Chain> {
    private final ApiConfiguration configuration;
    private final DSLContext db;

    @Inject
    public DumpComponent(ApiConfiguration configuration, DSLContext db) {
        this.configuration = configuration;
        this.db = db;
    }

    private void get(Context ctx, int id) {
        final Dump dump = db.fetchOne(DUMP, DUMP.ID.eq(id)).into(Dump.class);
        if (dump == null) {
            ctx.notFound();
        } else {
            ctx.render(Jackson.json(dump));
        }
    }

    private void getAll(Context ctx) {
        ctx.render(Jackson.json(db.fetch(DUMP).into(Dump.class)));
    }

    public static class DumpRequestMeta {
        public String title;
        public String description;
    }

    public static class DumpRequest {
        public DumpRequestMeta meta;
        public DumpSpecJson spec;
    }

    private void create(Context ctx, DumpRequest request, ObjectWriter objectWriter) throws JsonProcessingException {
        DumpRecord record = db.newRecord(DUMP);
        record.setTitle(request.meta.title);
        record.setDescription(request.meta.description);
        record.setSpec(objectWriter.writeValueAsString(request.spec));
        record.store();

        ctx.redirect(201, configuration.apiRoot + "/dump/" + record.getId());
    }

    /**
     * Register routes
     */
    @Override
    public void execute(Chain chain) throws Exception {
        chain.get("dump/:id", ctx -> {
            try {
                final int id = Integer.parseInt(ctx.getPathTokens().get("id"));
                this.get(ctx, id);
            } catch (NumberFormatException e) {
                ctx.notFound();
            }
        }).path("dumps", rootCtx -> rootCtx.byMethod(m ->
                m.get(this::getAll).post(ctx -> {
                    final ObjectWriter objectWriter = Jackson.getObjectWriter(chain.getRegistry());
                    ctx.parse(Jackson.fromJson(DumpRequest.class)).then(request -> this.create(ctx, request, objectWriter));
                })
        ));
    }
}
