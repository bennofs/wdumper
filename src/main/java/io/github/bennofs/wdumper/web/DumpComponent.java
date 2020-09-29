package io.github.bennofs.wdumper.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.inject.Inject;
import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.jooq.tables.records.DB_DumpRecord;
import io.github.bennofs.wdumper.model.Dump;
import io.github.bennofs.wdumper.model.DumpFullInfo;
import io.github.bennofs.wdumper.model.DumpRunZenodo;
import io.github.bennofs.wdumper.spec.DumpSpecJson;
import org.apache.commons.lang3.Range;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.jackson.Jackson;
import ratpack.server.PublicAddress;
import ratpack.util.MultiValueMap;

import java.net.URI;
import java.util.*;

import static io.github.bennofs.wdumper.jooq.Tables.DUMP;

/**
 * This component provides create and query operations for dumps.
 */
public class DumpComponent implements Action<Chain> {
    private final BaseComponent base;
    private final Database db;
    private final TemplateLoader loader;
    private final UrlBuilder urls;

    private final static Integer LIST_LIMIT = 10;

    @Inject
    public DumpComponent(BaseComponent base, Database db, TemplateLoader loader, UrlBuilder urls) {
        this.base = base;
        this.db = db;
        this.loader = loader;
        this.urls = urls;
    }

    private void get(Context ctx, int id) throws Exception {
        final Optional<DumpFullInfo> dump = db.getDumpWithFullInfo(id);

        if (dump.isEmpty()) {
            ctx.notFound();
            return;
        }

        ctx.byContent(spec -> spec
                .html(() -> ctx.render(loader.template(ctx, "dump.mustache", "info", dump.get())))
                .json(() -> ctx.render(Jackson.json(dump.get())))
        );
    }

    private void getAll(Context ctx) {
        ctx.render(Jackson.json(db.context().fetch(DUMP).into(Dump.class)));
    }

    public static class DumpRequestMeta {
        public String title;
        public String description;
    }

    public static class DumpRequest {
        public DumpRequestMeta metadata;
        public DumpSpecJson spec;
    }

    private void create(Context ctx, DumpRequest request, ObjectWriter objectWriter) throws JsonProcessingException {
        DB_DumpRecord record = db.context().newRecord(DUMP);
        record.setTitle(request.metadata.title);
        record.setDescription(request.metadata.description);
        record.setSpec(objectWriter.writeValueAsString(request.spec));
        record.store();

        final PublicAddress addr = ctx.get(PublicAddress.class);
        final String apiUrl = addr.builder().path("api/dump").segment("%d", record.getId()).build().toString();
        ctx.header("Location", apiUrl);
        ctx.getResponse().status(201);
        ctx.render(Jackson.json(Map.of(
            "view-url", addr.builder().path("dump").segment("%d", record.getId()).build().toString(),
            "api-url", apiUrl
        )));
    }

    private void listDumps(Context ctx) {
        final Map<String, Object> context = new HashMap<>();

        Integer firstId = null;
        Integer lastId = null;

        final MultiValueMap<String, String> params = ctx.getRequest().getQueryParams();
        if (params.containsKey("first")) {
            firstId = Integer.parseInt(params.get("first"));
        }
        if (params.containsKey("last")) {
            lastId = Integer.parseInt(params.get("last"));
        }

        final List<DumpRunZenodo> dumps;
        if (params.containsKey("first") && params.containsKey("last")) {
            throw new IllegalArgumentException("first and last cannot be combined");
        }
        if (lastId != null) {
            dumps = db.getRecentDumpsPrev(lastId, LIST_LIMIT, true);
        } else {
            dumps = db.getRecentDumpsNext(firstId, LIST_LIMIT);
        }

        final Optional<Range<Integer>> availableIds = db.getAllDumpsRange();
        final OptionalInt maxId = dumps.stream().mapToInt(d -> d.dump.id()).max();
        final OptionalInt minId = dumps.stream().mapToInt(d -> d.dump.id()).min();

        final PublicAddress addr = ctx.get(PublicAddress.class);
        context.put("dumps", dumps);

        if (maxId.isPresent() && availableIds.isPresent() && availableIds.get().getMaximum() > maxId.getAsInt()) {
            final URI uri = addr.builder().path("dumps").params("last", "" + maxId.getAsInt()).build();
            context.put("prevUrl", uri.toASCIIString());
        } else {
            context.put("prevUrl", null);
        }

        if (minId.isPresent() && availableIds.isPresent() && availableIds.get().getMinimum() < minId.getAsInt()) {
            final URI uri = addr.builder().path("dumps").params("first", "" + minId.getAsInt()).build();
            context.put("nextUrl", uri.toASCIIString());
        } else {
            context.put("nextUrl", null);
        }
        ctx.render(loader.template(ctx,"dumps.mustache", "", context));
    }

    /**
     * Register routes
     */
    @Override
    public void execute(Chain chain) throws Exception {
        urls.api(chain, apiChain -> apiChain
                .get("dump/:id", ctx -> {
                    try {
                        final int id = Integer.parseInt(ctx.getPathTokens().get("id"));
                        this.get(ctx, id);
                    } catch (NumberFormatException e) {
                        ctx.notFound();
                    }
                })
                .path("dumps", rootCtx -> rootCtx.byMethod(m -> m
                        .get(this::getAll)
                        .post(ctx -> {
                            final ObjectWriter objectWriter = Jackson.getObjectWriter(chain.getRegistry());
                            ctx.parse(Jackson.fromJson(DumpRequest.class)).then(request -> this.create(ctx, request, objectWriter));
                        })
                ))
        );

        chain.get("", ctx -> ctx.render(loader.template(ctx,"index.mustache", "create")));
        base.registerHealthCheck(new RouteHealthCheck(""));

        chain.get("dumps", this::listDumps);
        base.registerHealthCheck(new RouteHealthCheck("dumps"));

        chain.get("dump/:id", ctx -> {
            try {
                final int id = Integer.parseInt(ctx.getPathTokens().get("id"));
                this.get(ctx, id);
            } catch (NumberFormatException e) {
                ctx.notFound();
            }
        });

    }
}
