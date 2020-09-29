package io.github.bennofs.wdumper.web;

import com.samskivert.mustache.Template;
import ratpack.handling.Context;
import ratpack.http.Response;
import ratpack.render.Renderable;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

public class MustacheResponse implements Renderable {
    final Object context;
    final @Nullable Object layoutContext;
    final Template layout;
    final Template content;

    final String templateName;

    public MustacheResponse(Template layout, Object layoutContext, Template content, Object context, String templateName) {
        this.layout = layout;
        this.content = content;
        this.context = context;
        this.layoutContext = layoutContext;
        this.templateName = templateName;
    }

    @Override
    public void render(Context ctx) throws Exception {
        final Response r = ctx.getResponse();
        r.contentTypeIfNotSet("text/html");


        final ByteArrayOutputStream contentStream = new ByteArrayOutputStream(1000);
        try (final Writer writer = new OutputStreamWriter(contentStream)) {
            content.execute(context, layoutContext, writer);
        }

        final ByteArrayOutputStream result = new ByteArrayOutputStream(contentStream.size() + 1000);
        try (final Writer writer = new OutputStreamWriter(result)) {
            layout.execute(Map.of("content", contentStream.toString()), layoutContext, writer);
        }

        r.send(result.toByteArray());
    }

    @Override
    public String toString() {
        return String.format("MustacheResponse(name=\"%s\")", templateName);
    }
}
