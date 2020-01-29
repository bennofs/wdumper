package io.github.bennofs.wdumper.api;

import io.github.bennofs.wdumper.jooq.tables.daos.DumpDao;
import io.github.bennofs.wdumper.jooq.tables.pojos.Dump;
import ratpack.handling.Context;
import ratpack.handling.Handler;

public class HandlerDump implements Handler {
    private final int id;
    private final DumpDao dao;

    public HandlerDump(DumpDao dao, int id) {
        this.id = id;
        this.dao = dao;
    }

    @Override
    public void handle(Context rootCtx) throws Exception {
        rootCtx.byMethod(m -> m.get(ctx -> {
            final Dump dump = dao.fetchOneById(id);
            if (dump == null) {
                ctx.notFound();
            } else {
                ctx.render(dump);
            }
        }));
    }
}
