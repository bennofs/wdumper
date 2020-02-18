package io.github.bennofs.wdumper;

import com.google.common.base.MoreObjects;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.github.bennofs.wdumper.api.*;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import ratpack.func.Action;
import ratpack.guice.ConfigurableModule;
import ratpack.guice.Guice;
import ratpack.handling.Chain;
import ratpack.hikari.HikariModule;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;

import javax.sql.DataSource;
import java.net.URI;

/**
 * This is the main class providing the HTTP API for the frontend.
 * It also serves the static files used by the frontend.
 */
@Singleton
public class Api {
    public static class ApiModule extends ConfigurableModule<ApiConfiguration> {
        @Provides
        DSLContext db(DataSource dataSource) {
            final Configuration configuration = new DefaultConfiguration();
            configuration.set(dataSource);
            configuration.set(SQLDialect.MARIADB);
            return DSL.using(configuration);
        }

        @Override
        protected void configure() {
            bind(DumpComponent.class);
            bind(DownloadComponent.class);
            bind(StatusComponent.class);
            bind(ZenodoComponent.class);
        }
    }

    public static void main(String... args) throws Exception {
        final URI publicAddress = URI.create(
                MoreObjects.firstNonNull(System.getenv("PUBLIC_URL"), "http://localhost:5050")
        );
        final String root = publicAddress.getPath().replaceAll("/+$|^/+", "");
        final String apiRoot = (root.isEmpty() ? "" : ("/" + root)) + "/api";

        final Action<Chain> topRoutes = chain -> chain
                .prefix("api", api -> {
                    api.insert(DumpComponent.class);
                    api.insert(DownloadComponent.class);
                    api.insert(StatusComponent.class);
                    api.insert(ZenodoComponent.class);
                })
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
                        .module(ApiModule.class, apiConfig -> {
                            apiConfig.apiRoot(apiRoot);
                            apiConfig.publicAddress(publicAddress);
                        })
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
