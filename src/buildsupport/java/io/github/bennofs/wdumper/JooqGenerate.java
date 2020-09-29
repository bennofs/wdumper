package io.github.bennofs.wdumper;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.FileSystemResourceAccessor;
import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.*;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.MariaDBContainer;

import java.sql.Connection;

public class JooqGenerate {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: JooqGenerate LIQUIBASE_CHANGELOG.xml the/output/directory");
            System.exit(1);
        }

        try (MariaDBContainer<?> container = new MariaDBContainer<>()) {
            container.start();
            final MariaDbDataSource dataSource = new MariaDbDataSource();
            dataSource.setUrl(container.getJdbcUrl());
            dataSource.setUserName(container.getUsername());
            dataSource.setPassword(container.getPassword());

            try (final Connection conn = dataSource.getConnection()) {
                final Liquibase lq = new Liquibase(args[0],
                        new FileSystemResourceAccessor(),
                        new JdbcConnection(conn));
                lq.update(new Contexts());
            }

            final Configuration configuration = new Configuration()
                    .withJdbc(new Jdbc()
                            .withDriver(container.getDriverClassName())
                            .withUser(container.getUsername())
                            .withPassword(container.getPassword())
                            .withUrl(container.getJdbcUrl()))
                    .withGenerator(new Generator()
                            .withDatabase(new Database()
                                    .withInputSchema(container.getDatabaseName())
                                    .withOutputSchemaToDefault(true)
                                    .withOutputCatalogToDefault(true)
                                    .withName("org.jooq.meta.mariadb.MariaDBDatabase"))
                            .withTarget(new Target()
                                    .withDirectory(args[1])
                                    .withPackageName("io.github.bennofs.wdumper.jooq"))
                            .withGenerate(new Generate()
                                .withPojos(false))
                            .withStrategy(new Strategy()
                                    .withName(JooqStrategy.class.getName())));

            GenerationTool.generate(configuration);
        }
    }
}
