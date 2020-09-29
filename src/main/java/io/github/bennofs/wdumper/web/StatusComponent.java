package io.github.bennofs.wdumper.web;

import com.google.inject.Inject;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.handling.Context;
import ratpack.jackson.Jackson;

public class StatusComponent implements Action<Chain> {
    @Inject
    public StatusComponent() {
    }

    // TODO: implement proper status API
    private void get(Context ctx) {
        ctx.render(Jackson.jsonNode());
    }

    @Override
    public void execute(Chain chain) throws Exception {
        chain.get("status", this::get);
    }
}
