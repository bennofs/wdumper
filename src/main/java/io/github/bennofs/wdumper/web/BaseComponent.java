package io.github.bennofs.wdumper.web;

import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.health.HealthCheck;
import ratpack.health.HealthCheckHandler;
import ratpack.registry.MutableRegistry;
import ratpack.registry.Registry;
import ratpack.server.PublicAddress;

import javax.inject.Inject;

public class BaseComponent implements Action<Chain> {
    private final MutableRegistry healthChecks;
    private final TemplateLoader templateLoader;

    @Inject
    public BaseComponent(TemplateLoader templateLoader) {
        this.healthChecks = Registry.mutable();
        this.templateLoader = templateLoader;
    }


    public void registerHealthCheck(HealthCheck check) {
        this.healthChecks.add(check);
    }

    @Override
    public void execute(Chain chain) throws Exception {
        chain.prefix("static", c -> c.files(f -> f.dir("static")));
        chain.register(chain.getRegistry().join(this.healthChecks), c ->
                c.get("health/:name?", HealthCheckHandler.class)
        );

        chain.get("about", ctx -> ctx.render(templateLoader.template(ctx, "about.mustache", "")));
        registerHealthCheck(new RouteHealthCheck("about"));
    }
}
