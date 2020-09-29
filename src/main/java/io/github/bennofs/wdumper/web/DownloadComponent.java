package io.github.bennofs.wdumper.web;

import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;

public class DownloadComponent implements Action<Chain> {
    @Inject
    public DownloadComponent() {

    }

    private void get(Context ctx, int id) {
        final String fname = "wdump-" + id + ".nt.gz";
        final Path path = Path.of("dumpfiles/generated/" + fname);

        try {
            final BasicFileAttributes attrs = Files.getFileAttributeView(path, BasicFileAttributeView.class)
                    .readAttributes();
            ctx.getResponse().contentType("application/octet-stream");
            ctx.getResponse().getHeaders().add("Content-Length", attrs.size());
            ctx.getResponse().getHeaders().add("Content-Disposition", "attachment; filename=" + fname);
            ctx.getResponse().sendFile(path);
        } catch (NoSuchFileException e) {
            ctx.next();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void execute(Chain chain) throws Exception {
        chain.get("download/:id", ctx -> {
            try {
                this.get(ctx, Integer.parseInt(ctx.getPathTokens().get("id")));
            } catch (NumberFormatException e) {
                ctx.notFound();
            }
        });
    }
}
