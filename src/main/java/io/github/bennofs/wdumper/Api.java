package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.base.MoreObjects;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.samskivert.mustache.Mustache;
import com.zaxxer.hikari.pool.HikariPool;
import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.formatting.TimeFormatting;
import io.github.bennofs.wdumper.model.DumpRunZenodo;
import io.github.bennofs.wdumper.model.ModelExtension;
import io.github.bennofs.wdumper.web.*;
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
import ratpack.file.FileSystemBinding;
import ratpack.func.Action;
import ratpack.guice.Guice;
import ratpack.handling.Chain;
import ratpack.health.HealthCheckHandler;
import ratpack.hikari.HikariHealthCheck;
import ratpack.hikari.HikariModule;
import ratpack.server.BaseDir;
import ratpack.server.RatpackServer;
import ratpack.server.ServerConfig;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * This is the main class providing the HTTP API for the frontend.
 * It also serves the static files used by the frontend.
 */
public class Api  {
    public static class DbHealthCheck extends HikariHealthCheck {
        private final HikariPool pool;

        @Inject
        public DbHealthCheck(HikariPool pool) {
            this.pool = pool;
        }

        @Override
        public HikariPool getHikariPool() {
            return pool;
        }

        @Override
        public String getName() {
            return "db";
        }
    }

    public static class ApiModule extends AbstractModule {
        private final ZenodoConfiguration zenodoConfiguration;
        private final Config config;

        public ApiModule(Config config, ZenodoConfiguration zenodoConfiguration) {
            this.config = config;
            this.zenodoConfiguration = zenodoConfiguration;
        }

        @Provides
        DSLContext db(DataSource dataSource) {
            final Configuration configuration = new DefaultConfiguration();
            configuration.set(dataSource);
            configuration.set(SQLDialect.MARIADB);
            return DSL.using(configuration);
        }

        @Provides
        @Singleton
        CloseableHttpClient httpClient() {
            return HttpClientBuilder.create()
                    .build();
        }

        @Provides
        @Singleton
        BuildConfig buildConfig() {
            return BuildConfig.retrieve();
        }

        @Provides
        @Singleton
        Database database(DataSource dataSource, BuildConfig buildConfig) {
            return new Database(config, buildConfig, dataSource);
        }

        private static Mustache.Collector mustacheCollector(UrlBuilder urls, ProgressEstimator progressEstimator, TimeFormatting timeFormatting) {
            return CompositeCollector.builder()
                    .addExtension(ModelExtension.class, ModelExtension::extensionBase)
                    .addExtension(Timestamp.class, ts -> new DateTimeExt(ts.toInstant(), timeFormatting))
                    .addExtension(LocalDateTime.class, t -> new DateTimeExt(t.toInstant(ZoneOffset.UTC), timeFormatting))
                    .addExtension(Instant.class, t -> new DateTimeExt(t, timeFormatting))
                    .addExtension(Duration.class, d -> new DurationExt(d, timeFormatting))
                    .addExtension(DumpRunZenodo.class, v -> new DumpRunZenodoExt(urls, v, progressEstimator))
                    .build();
        }

        @Provides
        @Singleton
        TemplateLoader templateLoader(final FileSystemBinding files, UrlBuilder urls, ServerConfig config, ProgressEstimator progressEstimator) throws IOException {
            final FileSystemBinding templates = files.binding("templates/");
            final Mustache.Compiler compiler = Mustache.compiler()
                    .strictSections(true)
                    .withCollector(mustacheCollector(urls, progressEstimator, new TimeFormatting()))
                    .withLoader(name -> Files.newBufferedReader(templates.file(name)));
            return TemplateLoader.create(urls, compiler, config);
        }

        @Provides
        HealthCheckHandler healthCheckHandler() {
            return new HealthCheckHandler();
        }

        @Provides
        @Singleton
        ObjectMapper objectMapper() {
            final ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new Jdk8Module());
            objectMapper.registerModule(new ParameterNamesModule());
            objectMapper.registerModule(new JavaTimeModule());
            return objectMapper;
        }

        @Override
        protected void configure() {
            bind(ZenodoConfiguration.class).toInstance(zenodoConfiguration);

            bind(BaseComponent.class).in(Scopes.SINGLETON);
            bind(DumpComponent.class).in(Scopes.SINGLETON);
            bind(DownloadComponent.class).in(Scopes.SINGLETON);
            bind(StatusComponent.class).in(Scopes.SINGLETON);
            bind(ZenodoComponent.class).in(Scopes.SINGLETON);

            bind(ZenodoApiProvider.class).to(ZenodoApiProviderImpl.class);
        }
    }

    private static class BaseDirService implements Closeable {
        private final @Nullable Path tempDir;
        public final Path baseDir;

        BaseDirService(@Nullable Path tempDir, Path baseDir) {
            this.tempDir = tempDir;
            this.baseDir = baseDir;
        }

        public static BaseDirService find() throws IOException {
            try {
                return new BaseDirService(null, BaseDir.find());
            } catch (IllegalStateException e) {
                // some IDEs (such as intellij) don't run the gradle build and therefore don't have the real resources
                final Path resourcesBuild = Path.of("build/resources/main/").toAbsolutePath();
                if (!Files.isRegularFile(resourcesBuild.resolve(".ratpack"))) {
                    throw new IllegalStateException("cannot find base dir (directory containing .ratpack marker file)");

                }
                final Path resourcesSrc = Path.of("src/main/resources").toAbsolutePath();

                final Path tmp = Path.of("build/tmp");
                Files.createDirectories(tmp);
                final Path root = Files.createTempDirectory(tmp, "ratpackResources").toAbsolutePath();
                Files.createFile(root.resolve(".ratpack"));
                Files.createSymbolicLink(root.resolve("db"), resourcesSrc.resolve("db"));
                Files.createSymbolicLink(root.resolve("templates"), resourcesSrc.resolve("templates"));
                Files.createSymbolicLink(root.resolve("static"), resourcesBuild.resolve("static"));
                return new BaseDirService(root, root);
            }
        }

        @Override
        public void close() throws IOException {
            if (tempDir != null) {
                Files.delete(tempDir.resolve(".ratpack"));
                Files.delete(tempDir.resolve("db"));
                Files.delete(tempDir.resolve("templates"));
                Files.delete(tempDir.resolve("static"));
                Files.delete(tempDir);
            }
        }
    }

    public static void main(String... args) throws Exception {
        final Config config = new ConfigEnv();

        final URI publicAddress = URI.create(
                MoreObjects.firstNonNull(System.getenv("PUBLIC_URL"), "http://localhost:5050")
                        .replaceFirst("/*$", "/") // make sure it ends with exactly one slash
        );

        final var zenodoConfiguration = ZenodoConfiguration.builder()
                .releaseToken(config.zenodoReleaseToken().orElse(null))
                .sandboxToken(config.zenodoSandboxToken().orElse(null))
                .build();

        final Action<Chain> topRoutes = chain -> {
            chain.insert(DumpComponent.class);
            chain.insert(DownloadComponent.class);
            chain.insert(StatusComponent.class);
            chain.insert(ZenodoComponent.class);
            chain.insert(BaseComponent.class);
        };

        final BaseDirService baseDirService = BaseDirService.find();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                baseDirService.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        final ServerConfig serverConfig = ServerConfig.embedded()
                .port(5050)
                .publicAddress(publicAddress)
                .baseDir(baseDirService.baseDir)
                .build();

        if (serverConfig.isDevelopment()) {
        }

        // configure the ratpack web server
        RatpackServer.start(server -> server
                .serverConfig(serverConfig)
                .registry(Guice.registry(b -> b
                        .module(HikariModule.class, hikariConfig -> {
                            hikariConfig.setJdbcUrl(config.databaseAddress().toString());
                        })
                        .bind(DbHealthCheck.class)
                        .module(new ApiModule(config, zenodoConfiguration))
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
