package io.github.bennofs.wdumper.api;

import ratpack.handling.Context;
import ratpack.handling.Handler;

public class HandlerDownload implements Handler {
    private final int id;

    public HandlerDownload(int id) {
        this.id = id;
    }

    @Override
    public void handle(Context ctx) throws Exception {

    }
}
