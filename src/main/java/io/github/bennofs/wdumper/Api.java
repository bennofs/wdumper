package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.base.MoreObjects;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.samskivert.mustache.Mustache;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.model.DumpRunZenodo;
import io.github.bennofs.wdumper.model.ModelExtension;
import io.github.bennofs.wdumper.templating.*;
import io.github.bennofs.wdumper.web.DumpResource;
import io.github.bennofs.wdumper.web.ProgressEstimator;
import io.github.bennofs.wdumper.zenodo.ZenodoApiProvider;
import io.github.bennofs.wdumper.zenodo.ZenodoApiProviderImpl;
import io.github.bennofs.wdumper.zenodo.ZenodoConfiguration;
import io.github.bennofs.wdumper.zenodo.ZenodoResource;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jboss.resteasy.plugins.server.undertow.UndertowJaxrsServer;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import javax.ws.rs.core.Application;
import javax.ws.rs.ext.ContextResolver;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * This is the main class providing the HTTP API for the frontend.
 * It also serves the static files used by the frontend.
 */
public class Api extends Application {
    public static class ApiModule extends AbstractModule {
        private final ZenodoConfiguration zenodoConfiguration;
        private final Config config;
        private final URI publicAddress;
        private final Path templateRoot;

        public ApiModule(Config config, ZenodoConfiguration zenodoConfiguration, URI publicAddress, Path templateRoot) {
            this.config = config;
            this.zenodoConfiguration = zenodoConfiguration;
            this.publicAddress = publicAddress;
            this.templateRoot = templateRoot;
        }

        @Provides
        @Singleton
        DataSource dataSource() {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(config.databaseAddress().toString());
            return new HikariDataSource(hikariConfig);
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
        UrlBuilder urlBuilder() {
            return new UrlBuilder(publicAddress);
        }

        @Provides
        @Singleton
        TemplateLoader templateLoader(UrlBuilder urls, ProgressEstimator progressEstimator) throws IOException {
            final Mustache.Compiler compiler = Mustache.compiler()
                    .strictSections(true)
                    .withCollector(mustacheCollector(urls, progressEstimator, new TimeFormatting()))
                    .withLoader(name -> Files.newBufferedReader(templateRoot.resolve(name)));
            return TemplateLoader.create(urls, compiler, new TemplateLoader.Config() {
                @Override
                public Path baseDir() {
                    return Path.of("build/resources/main/");
                }

                @Override
                public boolean isDevelopment() {
                    return true; // TODO
                }
            });
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
            bind(ZenodoApiProvider.class).to(ZenodoApiProviderImpl.class);
        }
    }

    private final Set<Object> instances;

    @Inject
    public Api(DumpResource dump, ZenodoResource zenodo, ObjectMapper mapper) {
        instances = Set.of(dump, zenodo, new ContextResolver<ObjectMapper>() {
            @Override
            public ObjectMapper getContext(Class<?> type) {
                return mapper;
            }
        });
    }

    @Override
    public Set<Object> getSingletons() {
        return instances;
    }

    public static void main(String... args) throws URISyntaxException, IOException {
        final Config config = new ConfigEnv();

        final URI publicAddress = URI.create(
                MoreObjects.firstNonNull(System.getenv("PUBLIC_URL"), "http://localhost:5050")
                        .replaceFirst("/*$", "/") // make sure it ends with exactly one slash
        );

        final var zenodoConfiguration = ZenodoConfiguration.builder()
                .releaseToken(config.zenodoReleaseToken().orElse(null))
                .sandboxToken(config.zenodoSandboxToken().orElse(null))
                .build();

        final int port = ConfigEnv.intFromEnv("WDUMPER_PORT", 5050);
        final String host = Objects.requireNonNullElse(System.getenv("WDUMPER_HOST"), "localhost");

        final ClassLoader loader = Api.class.getClassLoader();
        final URL webrootFile = loader.getResource(".webroot");
        final Path templateRoot;
        final Path staticRoot;
        Closeable filesystem = null;
        UndertowJaxrsServer server = new UndertowJaxrsServer();

        // when running directly from IDE, the classpath doesn't include the gradle built resources
        if (webrootFile == null) {
            // the URL needs the end with a slash,
            // otherwise new URL(root, "foo") will replace the last component of root with "foo"
            templateRoot = Path.of( "src/main/resources/templates/").toAbsolutePath();
            staticRoot = Path.of("build/resources//main/static").toAbsolutePath();
        } else {
            if (webrootFile.toURI().getScheme().equals("jar")) {
                filesystem = FileSystems.newFileSystem(webrootFile.toURI(), Collections.emptyMap());
            }

            final Path base = Paths.get(new URL(webrootFile, "./").toURI());
            templateRoot = base.resolve("templates");
            staticRoot = base.resolve("static");
        }

        final ResourceManager resourceManager = new PathResourceManager(staticRoot);
        try {
            final Injector injector = Guice.createInjector(
                    new ApiModule(config, zenodoConfiguration, publicAddress, templateRoot)
            );
            final Api api = injector.getInstance(Api.class);
            server.addResourcePrefixPath("/static", new ResourceHandler(resourceManager));
            server.deploy(api);
            server.setHostname(host);
            server.setPort(port);
            server.start();
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            try {
                server.stop();
            } catch (NullPointerException ignored) {
            }
            resourceManager.close();

            if (filesystem != null) {
                filesystem.close();
            }
        }
    }
}
