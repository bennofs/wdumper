package io.github.bennofs.wdumper.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.jooq.tables.records.DB_DumpRecord;
import io.github.bennofs.wdumper.model.Dump;
import io.github.bennofs.wdumper.model.DumpFullInfo;
import io.github.bennofs.wdumper.model.DumpRunZenodo;
import io.github.bennofs.wdumper.spec.DumpSpecJson;
import io.github.bennofs.wdumper.templating.TemplateLoader;
import io.github.bennofs.wdumper.templating.UrlBuilder;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.Range;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;

import static io.github.bennofs.wdumper.jooq.Tables.DUMP;

@Path("/")
public class DumpResource {
    private final TemplateLoader template;
    private final Database db;
    private final UrlBuilder urlBuilder;
    private final ObjectWriter objectWriter;

    private final static Integer LIST_LIMIT = 10;

    @Inject
    public DumpResource(TemplateLoader template, Database db, UrlBuilder urlBuilder, ObjectMapper mapper) {
        this.template = template;
        this.db = db;
        this.urlBuilder = urlBuilder;
        this.objectWriter = mapper.writer();
    }


    @GET
    @Path("/dump/{id}")
    @Produces("text/html")
    public byte[] getDumpHtml(@PathParam("id") int id) throws Exception {
        return template.render("dump.mustache", "info", getDumpJson(id));
    }

    @GET
    @Path("/dump/{id}")
    @Produces("application/json")
    public DumpFullInfo getDumpJson(@PathParam("id") int id) {
        final Optional<DumpFullInfo> dump = db.getDumpWithFullInfo(id);

        if (dump.isEmpty()) {
            throw new NotFoundException(String.format("there is no dump with id %d", id));
        }

        return dump.get();
    }

    @GET
    @Produces("application/json")
    @Path("/dump")
    public List<Dump> getAll() {
        return db.context().fetch(DUMP).into(Dump.class);
    }

    public static class DumpRequestMeta {
        public String title;
        public String description;
    }

    public static class DumpRequest {
        public DumpResource.DumpRequestMeta metadata;
        public DumpSpecJson spec;
    }

    @POST
    @Path("/dumps")
    @Produces("application/json")
    @Consumes("application/json")
    public Response create(DumpResource.DumpRequest request) throws JsonProcessingException {
        DB_DumpRecord record = db.context().newRecord(DUMP);
        record.setTitle(request.metadata.title);
        record.setDescription(request.metadata.description);
        record.setSpec(objectWriter.writeValueAsString(request.spec));
        record.store();

        final URI location = urlBuilder.urlPath(String.format("dump/%d", record.getId()));
        return Response.created(location).build();
    }

    @GET
    @Produces("text/html")
    @Path("/dumps")
    public byte[] listDumps(@QueryParam("first") Integer firstId, @QueryParam("last") Integer lastId) throws Exception {
        final Map<String, Object> context = new HashMap<>();

        final List<DumpRunZenodo> dumps;
        if (firstId != null && lastId != null) {
            System.err.println("lol fail");
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

        context.put("dumps", dumps);

        if (maxId.isPresent() && availableIds.isPresent() && availableIds.get().getMaximum() > maxId.getAsInt()) {
            final URI uri = urlBuilder.urlPath(String.format("dumps?last=%d", maxId.getAsInt()));
            context.put("prevUrl", uri.toASCIIString());
        } else {
            context.put("prevUrl", null);
        }

        if (minId.isPresent() && availableIds.isPresent() && availableIds.get().getMinimum() < minId.getAsInt()) {
            final URI uri = urlBuilder.urlPath(String.format("dumps?first=%d", minId.getAsInt()));
            context.put("nextUrl", uri.toASCIIString());
        } else {
            context.put("nextUrl", null);
        }

        return template.render("dumps.mustache", "", context);
    }

    @GET
    @Path("/download/{id}")
    public Response download(@PathParam("id") long id) throws IOException {
        final String fname = "wdump-" + id + ".nt.gz";
        final java.nio.file.Path path = java.nio.file.Path.of("dumpfiles/generated/" + fname);
        if (!Files.exists(path)) {
            throw new NotFoundException(String.format("there is no dump file for dump with id %d", id));
        }

        return Response.ok(path.toFile())
                .header("Content-Disposition", "attachment; filename=" + fname)
                .build();
    }

    @GET
    public byte[] index() throws Exception {
        return template.render("index.mustache", "create");
    }
}
