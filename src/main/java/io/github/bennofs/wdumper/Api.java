package io.github.bennofs.wdumper;

import com.google.common.base.MoreObjects;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.github.bennofs.wdumper.api.*;
import io.github.bennofs.wdumper.jooq.tables.daos.DumpDao;
import org.jooq.Configuration;
import org.jooq.SQLDialect;
import org.jooq.impl.DefaultConfiguration;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.handling.Chain;
import ratpack.hikari.HikariModule;
import ratpack.jackson.Jackson;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;
import ratpack.service.Service;

import javax.sql.DataSource;
import java.net.URI;

/**
 * This is the main class providing the HTTP API for the frontend.
 * It also serves the static files used by the frontend.
 */
@Singleton
public class Api implements Service, Action<Chain> {
    private final Configuration configuration;

    @Inject
    public Api(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Defines the routes for the HTTP API.
     */
    @Override
    public void execute(Chain chain) throws Exception {
        final DumpDao dumpDao = new DumpDao(configuration);

        chain.path("dump/:id:[\\d]+", ctx -> {
            final int id = Integer.parseInt(ctx.getPathTokens().get("id"));
            ctx.insert(new HandlerDump(dumpDao, id));
        }).path("dumps", ctx -> {
            ctx.insert(new HandlerDumps(dumpDao, Jackson.getObjectWriter(chain.getRegistry())));
        }).path("download/:id:[\\d]+", ctx -> {
            final int id = Integer.parseInt(ctx.getPathTokens().get("id"));
            ctx.insert(new HandlerDownload(id));
        }).path("zenodo", ctx -> {
            ctx.insert(new HandlerZenodo());
        }).path("status", ctx -> {
            ctx.insert(new HandlerStatus());
        });
    }

    public static class ApiModule extends AbstractModule {
        @Provides
        Configuration configuration(DataSource dataSource) {
            final Configuration configuration = new DefaultConfiguration();
            configuration.set(dataSource);
            configuration.set(SQLDialect.MARIADB);
            return configuration;
        }

        @Override
        protected void configure() {
            bind(Api.class);
        }
    }

    public static void main(String... args) throws Exception {
        final URI publicAddress = URI.create(
                MoreObjects.firstNonNull(System.getenv("PUBLIC_URL"), "http://localhost:5050")
        );

        final Action<Chain> topRoutes = chain -> chain
                .prefix("api", Api.class)
                .files(f -> f.dir("public").indexFiles("index.html"));

        // configure the ratpack web server
        RatpackServer.start(server -> server
                .serverConfig(ServerConfig.embedded()
                        .port(5050)
                        .publicAddress(publicAddress)
                        .baseDir(BaseDir.find())
                )
                .registry(Guice.registry(b -> b
                        .module(HikariModule.class, hikariConfig -> {
                            hikariConfig.setDataSourceClassName("com.mysql.cj.jdbc.MysqlDataSource");
                            hikariConfig.addDataSourceProperty("URL", Config.constructDBUri());
                        })
                        .module(ApiModule.class)
                ))
                .handlers(chain -> {
                    final String prefix = publicAddress.getPath().replaceAll("/+$|^/+", "");
                    if (prefix.isEmpty()) {
                        topRoutes.execute(chain);
                    } else {
                        chain.prefix(prefix, topRoutes);
                    }
                })
        );
    }
}
