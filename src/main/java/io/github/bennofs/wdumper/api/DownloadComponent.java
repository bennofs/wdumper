package io.github.bennofs.wdumper.api;

import com.google.inject.Inject;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;

public class DownloadComponent implements Action<Chain> {
    @Inject
    public DownloadComponent() {

    }

    private void get(Context ctx, int id) {

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
