package io.github.bennofs.wdumper.database;

import io.github.bennofs.wdumper.BuildConfig;
import io.github.bennofs.wdumper.model.*;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@Testcontainers
public class DatabaseTest {
    @Container
    final static MariaDBContainer<?> MARIA_DB_CONTAINER = new MariaDBContainer<>();
    final static MariaDbDataSource DATA_SOURCE = new MariaDbDataSource();

    final static BuildConfig TEST_BUILD_CONFIG = new BuildConfig() {
        @Override
        public String wdtkVersion() {
            return "test";
        }

        @Override
        public String toolVersion() {
            return "test";
        }
    };

    final Database db = new Database(new Database.Config() {
        @Override
        public Duration minDumpRequestAge() {
            return Duration.of(10, ChronoUnit.MINUTES);
        }

        @Override
        public Duration maxDumpRequestAge() {
            return Duration.of(20, ChronoUnit.MINUTES);
        }
    }, TEST_BUILD_CONFIG, DATA_SOURCE);

    private static final String SPEC = "{\"sitelinks\":false,\"version\":\"1\",\"aliases\":false,\"entities\":" +
            "[{\"type\":\"item\",\"id\":2,\"properties\":[{\"type\":\"anyvalue\",\"rank\":\"non-deprecated\"," +
            "\"property\":\"P698\",\"id\":1},{\"type\":\"anyvalue\",\"rank\":\"non-deprecated\",\"property\":" +
            "\"P932\",\"id\":3}]}],\"statements\":[{\"id\":0,\"references\":false,\"simple\":false,\"rank\":" +
            "\"all\",\"full\":false,\"qualifiers\":false},{\"id\":4,\"references\":false,\"simple\":true," +
            "\"properties\":[],\"rank\":\"all\",\"full\":false,\"qualifiers\":false},{\"id\":5,\"references\":false," +
            "\"simple\":true,\"properties\":[],\"rank\":\"all\",\"full\":false,\"qualifiers\":false}],\"labels\":false," +
            "\"meta\":true,\"descriptions\":false}";

    private static final Dump DUMP_1 = Dump.builder()
            .id(1)
            .compressedSize(999L)
            .createdAt(Instant.ofEpochSecond(1591456900))
            .description("test dump with full associated data")
            .title("test1")
            .spec(SPEC)
            .entityCount(312L)
            .statementCount(2313L)
            .tripleCount(123891L)
            .runId(1)
            .build();

    private static final Run RUN_1 = Run.builder()
            .id(1)
            .count(60000000)
            .dumpDate("20200601")
            .finishedAt(Instant.ofEpochSecond(1591457500))
            .startedAt(Instant.ofEpochSecond(1591457000))
            .toolVersion("14dbd55d36082327e101adfc077b9cb72dfb8863")
            .wdtkVersion("0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
            .build();

    private static final Zenodo ZENODO_1_SANBDOX = Zenodo.builder()
            .id(1)
            .depositId(12389)
            .dumpId(1)
            .doi("10.5281/zenodo.3333333")
            .target(Zenodo.Target.SANDBOX)
            .createdAt(Instant.ofEpochSecond(1591457200))
            .startedAt(Instant.ofEpochSecond(1591457800))
            .completedAt(Instant.ofEpochSecond(1591457900))
            .uploadedBytes(999L)
            .build();

    private static final Zenodo ZENODO_1_RELEASE = Zenodo.builder()
            .id(2)
            .depositId(12390)
            .dumpId(1)
            .doi("10.5281/zenodo.1111111")
            .target(Zenodo.Target.RELEASE)
            .createdAt(Instant.ofEpochSecond(1591458200))
            .startedAt(Instant.ofEpochSecond(1591458800))
            .uploadedBytes(333L)
            .build();

    private static final DumpError ERROR_DUMP_1_WARN = DumpError.builder()
            .id(1)
            .loggedAt(Instant.ofEpochSecond(1591457100))
            .dumpId(1)
            .runId(1)
            .level(DumpError.Level.WARNING)
            .message("test dump warning")
            .build();

    private static final DumpError ERROR_ZENODO_1_WARNING = DumpError.builder()
            .id(2)
            .loggedAt(Instant.ofEpochSecond(1591457900))
            .dumpId(1)
            .zenodoId(1)
            .level(DumpError.Level.WARNING)
            .message("test zenodo warning sandbox")
            .build();

    private static final DumpError ERROR_ZENODO_2_CRITICAL = DumpError.builder()
            .id(3)
            .loggedAt(Instant.ofEpochSecond(1591458300))
            .dumpId(1)
            .zenodoId(2)
            .level(DumpError.Level.CRITICAL)
            .message("test zenodo critical release")
            .build();

    private static final DumpError ERROR_RUN_1_ERROR = DumpError.builder()
            .id(4)
            .loggedAt(Instant.ofEpochSecond(1591457200))
            .runId(1)
            .level(DumpError.Level.ERROR)
            .message("test run error")
            .build();

    private static final Dump DUMP_3 = Dump.builder()
            .id(3)
            .title("test3")
            .spec(SPEC)
            .description("test dump (created, but no run, with zenodo request)")
            .createdAt(Instant.ofEpochSecond(1591459000))
            .compressedSize(0L)
            .entityCount(0L)
            .statementCount(0L)
            .tripleCount(0L)
            .build();

    private static final Zenodo ZENODO_3_SANDBOX = Zenodo.builder()
            .id(3)
            .depositId(42)
            .dumpId(3)
            .doi("10.5281/zenodo.4444444")
            .target(Zenodo.Target.SANDBOX)
            .createdAt(Instant.ofEpochSecond(1591459200))
            .uploadedBytes(0)
            .build();

    private static final Run RUN_2 = Run.builder()
            .id(2)
            .startedAt(Instant.ofEpochSecond(1591461000))
            .count(20000000)
            .dumpDate("20200608")
            .toolVersion("14dbd55d36082327e101adfc077b9cb72dfb8863")
            .wdtkVersion("0afe49fef56a6da58a8f8fd3f023a56920c04f3b")
            .build();

    private static final Dump DUMP_4 = Dump.builder()
            .id(4)
            .title("test4")
            .spec(SPEC)
            .description("test dump (in-progress)")
            .createdAt(Instant.ofEpochSecond(1591460000))
            .tripleCount(0L)
            .entityCount(0L)
            .statementCount(0L)
            .compressedSize(10L)
            .runId(2)
            .build();

    private static final Dump DUMP_5 = Dump.builder()
            .id(5)
            .title("test5")
            .spec(SPEC)
            .description("test dump (in-progress)")
            .createdAt(Instant.ofEpochSecond(1591460300))
            .tripleCount(5000000000L)
            .entityCount(5000000L)
            .statementCount(9000000L)
            .compressedSize(1000000000L)
            .runId(2)
            .build();

    @BeforeAll
    static void initDb() throws SQLException, LiquibaseException, IOException {
        DATA_SOURCE.setUrl(MARIA_DB_CONTAINER.getJdbcUrl());
        DATA_SOURCE.setUserName(MARIA_DB_CONTAINER.getUsername());
        DATA_SOURCE.setPassword(MARIA_DB_CONTAINER.getPassword());

        try (final Connection conn = DATA_SOURCE.getConnection()) {
            final Liquibase lq = new Liquibase("db/changelog.xml",
                    new ClassLoaderResourceAccessor(DatabaseTest.class.getClassLoader()),
                    new JdbcConnection(conn));

            try (final StringWriter writer = new StringWriter()) {
                lq.update(new Contexts(), writer);
                MARIA_DB_CONTAINER.copyFileToContainer(
                        Transferable.of(writer.toString().getBytes()),
                        "/tmp/schema.sql");
            }
        }
    }

    @BeforeEach
    void resetDb() throws SQLException, IOException, InterruptedException {
        try (final Connection conn = DATA_SOURCE.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP DATABASE " + MARIA_DB_CONTAINER.getDatabaseName());
                stmt.executeUpdate("CREATE DATABASE " + MARIA_DB_CONTAINER.getDatabaseName());
                stmt.executeUpdate("USE " + MARIA_DB_CONTAINER.getDatabaseName());
            }
        }

        final ExecResult exec = MARIA_DB_CONTAINER.execInContainer("bash", "-c",
                String.format("mysql -u%s -p%s %s < /tmp/schema.sql",
                        MARIA_DB_CONTAINER.getUsername(),
                        MARIA_DB_CONTAINER.getPassword(),
                        MARIA_DB_CONTAINER.getDatabaseName()
                ));
        if (exec.getExitCode() != 0) {
            fail("applying mysql schema returned non-zero exit code: " + exec.getExitCode() + "\n"
                    + "# stderr:\n"
                    + exec.getStderr() + "\n\n"
                    + "# stdout:\n"
                    + exec.getStdout()
            );
        }

    }

    @Test
    void testGetDump() {
        assertEquals(Optional.of(DUMP_1), db.getDump(1));
        assertEquals(Optional.empty(), db.getDump(2));
        assertEquals(Optional.of(DUMP_3), db.getDump(3));
        assertEquals(Optional.of(DUMP_4), db.getDump(4));
        assertEquals(Optional.of(DUMP_5), db.getDump(5));
    }

    @Test
    void testGetRun() {
        assertEquals(Optional.of(RUN_1), db.getRun(1));
        assertEquals(Optional.of(RUN_2), db.getRun(2));
        assertEquals(Optional.empty(), db.getRun(1111));
    }

    @Test
    void testCreateRun() {
        final Optional<RunTask> task = db.createRun("20200101");
        assertTrue(task.isPresent(), "there are unassigned runs which should be assigned");

        // only dump3 is unassigned
        assertEquals(task.get().dumps, List.of(new DumpTask(DUMP_3.id(), DUMP_3.spec())));

        // run has been created
        final Optional<Run> run = db.getRun(task.get().runId);
        assertTrue(run.isPresent(), "run has been created in the db");
        assertEquals(Optional.empty(), run.get().finishedAt());
        assertEquals(Optional.empty(), run.get().startedAt());
        assertEquals("20200101", run.get().dumpDate());
        assertEquals(0, run.get().count());

        // now all dumps are claimed
        assertTrue(db.createRun("20200101").isEmpty(), "second time there are no unassigned dumps");

        // the DUMP_3 was updated in the db with the run
        final Dump d3 = db.getDump(3).get();
        assertTrue(d3.runId().isPresent(), "dump 3 now has an assigned run");
        assertEquals(d3.runId().get(), task.get().runId);
    }

    @Test
    void testStartRun() {
        final Instant before = Instant.now().minus(10, ChronoUnit.SECONDS);
        db.startRun(3);
        final Run r = db.getRun(3).get();
        final Instant after = Instant.now().plus(10, ChronoUnit.SECONDS);
        assertTrue(r.startedAt().isPresent(), "run has start time");
        assertThat(r.startedAt().get()).isBetween(before, after);
    }

    @Test
    void testFinishRun() {
        final Instant before = Instant.now().minus(10, ChronoUnit.SECONDS);
        db.finishRun(2);
        final Run r = db.getRun(2).get();
        final Instant after = Instant.now().plus(10, ChronoUnit.SECONDS);
        assertThat(r.finishedAt()).isPresent();
        assertThat(r.finishedAt().get()).isBetween(before, after);
    }

    @Test
    void testGetDumpWithFullInfo() {
        final DumpFullInfo dump1 = db.getDumpWithFullInfo(1).get();

        assertNotNull(dump1.run);
        assertNotNull(dump1.zenodoSandbox);
        assertNotNull(dump1.zenodoRelease);

        assertEquals(DUMP_1, dump1.dump);
        assertEquals(RUN_1, dump1.run.value);
        assertEquals(ZENODO_1_SANBDOX, dump1.zenodoSandbox.value);
        assertEquals(ZENODO_1_RELEASE, dump1.zenodoRelease.value);
        assertThat(dump1.errors.stream().map(v -> v.value).collect(Collectors.toList())).containsExactlyInAnyOrder(
                ERROR_DUMP_1_WARN,
                ERROR_RUN_1_ERROR,
                ERROR_ZENODO_1_WARNING,
                ERROR_ZENODO_2_CRITICAL);

        final DumpFullInfo dump3 = db.getDumpWithFullInfo(3).get();
        assertNull(dump3.run);
        assertNull(dump3.zenodoRelease);
        assertNotNull(dump3.zenodoSandbox);
        assertEquals(DUMP_3, dump3.dump);
        assertEquals(0, dump3.errors.size());
        assertEquals(ZENODO_3_SANDBOX, dump3.zenodoSandbox.value);

        final DumpFullInfo dump4 = db.getDumpWithFullInfo(4).get();
        assertNotNull(dump4.run);
        assertNull(dump4.zenodoSandbox);
        assertNull(dump4.zenodoRelease);
        assertEquals(0, dump4.errors.size());
        assertEquals(DUMP_4, dump4.dump);
        assertEquals(RUN_2, dump4.run.value);

        final DumpFullInfo dump5 = db.getDumpWithFullInfo(5).get();
        assertNotNull(dump5.run);
        assertNull(dump5.zenodoSandbox);
        assertNull(dump5.zenodoRelease);
        assertEquals(0, dump5.errors.size());
        assertEquals(DUMP_5, dump5.dump);
        assertEquals(RUN_2, dump5.run.value);

        assertTrue(db.getDumpWithFullInfo(11111).isEmpty(), "no dump with id 11111");
    }

    @Test
    void testGetRecentRunStats() {
        RunStats stats = db.getRecentRunStats();
        assertEquals(stats.entityCount, 60000000);
        assertThat(stats.averageTimePerEntity).isBetween(Duration.ofNanos(8333), Duration.ofNanos(8334));
    }

    @Test
    void testRecentDumpsNext() {
        final List<DumpRunZenodo> dumps = db.getRecentDumpsNext(null, 3);
        assertThat(dumps).element(0).extracting(d -> d.dump.id()).isEqualTo(5);
        assertThat(dumps).last().extracting(d -> d.dump.id()).isEqualTo(3);
        assertThat(dumps).size().isEqualTo(3);

        final List<DumpRunZenodo> limited = db.getRecentDumpsNext(3, 10);
        assertThat(limited).first().extracting(d -> d.dump.id()).isEqualTo(1);
        assertThat(limited).size().isEqualTo(1);
    }

    @Test
    void testRecentDumpsPrev() {
        final List<DumpRunZenodo> limited = db.getRecentDumpsPrev(1, 3, true);
        assertThat(limited).first().extracting(d -> d.dump.id()).isEqualTo(5);
        assertThat(limited).last().extracting(d -> d.dump.id()).isEqualTo(3);
        assertThat(limited).size().isEqualTo(3);

        final List<DumpRunZenodo> extended = db.getRecentDumpsPrev(3, 3, true);
        assertEquals(limited, extended);

        final List<DumpRunZenodo> notExtended = db.getRecentDumpsPrev(3, 3, false);
        assertEquals(limited.subList(0, 2), notExtended);

        final List<DumpRunZenodo> partial = db.getRecentDumpsPrev(1, 2, true);
        assertThat(partial).first().extracting(d -> d.dump.id()).isEqualTo(4);
        assertThat(partial).last().extracting(d -> d.dump.id()).isEqualTo(3);
        assertThat(partial).size().isEqualTo(2);
    }

    @Test
    void testGetDumpSpec() {
        final String retrieved = db.getDumpSpec(DUMP_1.id());
        assertEquals(DUMP_1.spec(), retrieved);
    }
}
