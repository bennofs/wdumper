package io.github.bennofs.wdumper;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.FileSystemResourceAccessor;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.MariaDBContainer;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Scanner;

public class DevDbServer {

    private static void provision(DataSource dataSource, String lqfile) throws SQLException, LiquibaseException {
        try (final Connection conn = dataSource.getConnection()) {
            final Liquibase lq = new Liquibase(lqfile,
                    new FileSystemResourceAccessor(),
                    new JdbcConnection(conn));
            lq.update(new Contexts());
        }
    }

    public static void main(String[] args) throws Exception {
        final String lqfile = args[0];

        try (MariaDBContainer<?> container = new MariaDBContainer<>()) {
            container.start();
            final MariaDbDataSource dataSource = new MariaDbDataSource();
            dataSource.setUrl(container.getJdbcUrl());
            dataSource.setUserName(container.getUsername());
            dataSource.setPassword(container.getPassword());

            final String auth = String.format("user=%s&password=%s",
                    URLEncoder.encode(container.getUsername(), Charset.defaultCharset()),
                    URLEncoder.encode(container.getPassword(), Charset.defaultCharset()));
            final URI fullJdbcUrl = new URI("jdbc:mariadb",
                    null,
                    container.getHost(),
                    container.getMappedPort(3306),
                    "/" + container.getDatabaseName(),
                    auth,
                    null);

            Files.createDirectories(Path.of("build/run"));
            Files.writeString(Path.of("build/run/db-url"), fullJdbcUrl.toString());

            final Scanner stdinScanner = new Scanner(System.in);
            while (true) {
                provision(dataSource, lqfile);
                System.out.println("{}");
                stdinScanner.nextLine();
            }
        }
    }
}
