package io.github.bennofs.wdumper;

import com.google.common.base.MoreObjects;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import io.github.bennofs.wdumper.api.*;
import io.github.bennofs.wdumper.jooq.enums.ZenodoTarget;
import io.github.bennofs.wdumper.zenodo.ZenodoApiProvider;
import io.github.bennofs.wdumper.zenodo.ZenodoApiProviderImpl;
import io.github.bennofs.wdumper.zenodo.ZenodoConfiguration;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.handling.Chain;
import ratpack.hikari.HikariModule;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;

import javax.inject.Singleton;
import javax.sql.DataSource;
import java.net.URI;

/**
 * This is the main class providing the HTTP API for the frontend.
 * It also serves the static files used by the frontend.
 */
@Singleton
public class Api {
    public static class ApiModule extends AbstractModule {
        private final ZenodoConfiguration zenodoConfiguration;
        private final ApiConfiguration apiConfiguration;

        public ApiModule(ZenodoConfiguration zenodoConfiguration, ApiConfiguration apiConfiguration) {
            this.zenodoConfiguration = zenodoConfiguration;
            this.apiConfiguration = apiConfiguration;
        }

        @Provides
        DSLContext db(DataSource dataSource) {
            final Configuration configuration = new DefaultConfiguration();
            configuration.set(dataSource);
            configuration.set(SQLDialect.MARIADB);
            return DSL.using(configuration);
        }

        @Provides @Singleton
        CloseableHttpClient httpClient() {
            return HttpClientBuilder.create()
                    .build();
        }

        @Override
        protected void configure() {
            bind(ZenodoConfiguration.class).toInstance(zenodoConfiguration);
            bind(ApiConfiguration.class).toInstance(apiConfiguration);

            bind(DumpComponent.class);
            bind(DownloadComponent.class);
            bind(StatusComponent.class);
            bind(ZenodoComponent.class);
            bind(ZenodoApiProvider.class).to(ZenodoApiProviderImpl.class);
        }
    }

    public static void main(String... args) throws Exception {
        final URI publicAddress = URI.create(
                MoreObjects.firstNonNull(System.getenv("PUBLIC_URL"), "http://localhost:5050")
        );
        final String root = publicAddress.getPath().replaceAll("/+$|^/+", "");

        final var zenodoConfiguration = ZenodoConfiguration.builder()
                .releaseToken(Config.getZenodoToken(ZenodoTarget.RELEASE))
                .sandboxToken(Config.getZenodoToken(ZenodoTarget.SANDBOX))
                .build();

        final ApiConfiguration apiConfig = ApiConfiguration.builder()
                .publicAddress(publicAddress)
                .apiRoot((root.isEmpty() ? "" : ("/" + root)) + "/api")
                .build();

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
                        .module(new ApiModule(zenodoConfiguration, apiConfig))
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
