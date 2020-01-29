package io.github.bennofs.wdumper.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.bennofs.wdumper.jooq.tables.daos.DumpDao;
import io.github.bennofs.wdumper.jooq.tables.records.DumpRecord;
import io.github.bennofs.wdumper.spec.DumpSpec;
import org.jooq.impl.DSL;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.jackson.Jackson;

import static io.github.bennofs.wdumper.jooq.Tables.DUMP;

public class HandlerDumps implements Handler {
    private final DumpDao dao;
    private final ObjectWriter objectWriter;

    public HandlerDumps(DumpDao dao, ObjectWriter objectWriter) {
        this.dao = dao;
        this.objectWriter = objectWriter;
    }

    public static class DumpRequestMeta {
        public String title;
        public String description;
    }

    public static class DumpRequest {
        public DumpRequestMeta meta;
        public DumpSpec spec;
    }

    private void handlePost(Context ctx, DumpRequest request) throws JsonProcessingException {
        // TODO: validate request

        DumpRecord record = DSL.using(dao.configuration()).newRecord(DUMP);
        record.setTitle(request.meta.title);
        record.setDescription(request.meta.description);
        record.setSpec(objectWriter.writeValueAsString(request.spec));
        record.store();

        ctx.redirect(201, "/dump/" + record.getId());
    }

    private void handleGet(Context ctx) {

    }

    @Override
    public void handle(Context rootCtx) throws Exception {
        rootCtx.byMethod(m -> m
                .post(ctx -> ctx.parse(Jackson.fromJson(DumpRequest.class)).then(r -> this.handlePost(ctx, r)))
                .get(this::handleGet)
        );
    }
}
