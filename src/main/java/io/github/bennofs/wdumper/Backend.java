package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.mysql.cj.jdbc.MysqlDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.database.DumpTask;
import io.github.bennofs.wdumper.database.RunTask;
import io.github.bennofs.wdumper.ext.ZstdDumpFile;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.processors.FilteredRdfSerializer;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.zenodo.ZenodoApi;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import picocli.CommandLine;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The main class for the backend application.
 */
public class Backend implements Runnable, Closeable {

    @CommandLine.Parameters(paramLabel = "DUMP", arity = "1", index = "0", description = "JSON dump from wikidata to process")
    private Path dumpFilePath;

    @CommandLine.Option(names = {"-d", "--dump-dir"}, paramLabel = "DIR", description = "directory where the generated dumps are stored", defaultValue = "dumpfiles/generated")
    private Path outputDirectory;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean usageHelpRequested;

    private final Config config;
    private final Database db;
    private final ZenodoApi zenodo;
    private final ZenodoApi zenodoSandbox;
    final Object runCompletedEvent;

    private Backend(Config config, Database db, ZenodoApi zenodo, ZenodoApi zenodoSandbox) {
        this.config = config;
        this.db = db;
        this.zenodo = zenodo;
        this.zenodoSandbox = zenodoSandbox;
        this.runCompletedEvent = new Object();
    }

    @Override
    public void close() {
    }

    private MwDumpFile openDumpFile() {
        Path resolvedPath = dumpFilePath;
        // resolve the dump file path, if possible
        // this is necessary to correctly determine the dump date from the filename,
        // since the filename of the link may be different from the actual dump filename.
        try {
            resolvedPath = this.dumpFilePath.toRealPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ZstdDumpFile(resolvedPath.toString());
    }

    private DumpRunner createRunner(int runId, List<DumpTask> tasks, MwDumpFile dumpFile) {
        final DumpRunner runner = DumpRunner.create(runId, config, dumpFile);

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(new Jdk8Module());
        mapper.registerModule(new ParameterNamesModule());

        for (DumpTask task : tasks) {
            try {
                runner.addDumpTask(task.id, mapper.readValue(task.spec, DumpSpec.class), (level, message) -> db.logDumpMessage(runId, task.id, level, message));
            } catch(IOException e) {
                db.logDumpMessage(runId, task.id, DumpStatusHandler.ErrorLevel.CRITICAL, "initialization failed: " + e.toString());
                e.printStackTrace();
            }
        }

        return runner;
    }


    private void processDumps() throws InterruptedException {
        System.out.println("checking for new tasks");
        final MwDumpFile dumpFile = openDumpFile();
        final Optional<RunTask> maybeRunTask = db.createRun(dumpFile.getDateStamp());

        // no new tasks
        if (maybeRunTask.isEmpty()) {
            TimeUnit.MILLISECONDS.sleep(config.dumpInterval().toMillis());
            return;
        }
        final RunTask runTask = maybeRunTask.get();
        final DumpRunner runner = createRunner(runTask.runId, runTask.dumps, dumpFile);

        runner.run(new RunnerStatusHandler() {
            @Override
            public void start() {
                db.startRun(runner.getId());
            }

            @Override
            public void reportProgress(int count) {
                db.setProgress(runner.getId(), count);
                // update statistics for all dumps
                for (FilteredRdfSerializer serializer : runner.getSerializers()) {
                    db.setDumpStatistics(serializer.getDumpId(), serializer.getEntityCount(), serializer.getStatementCount(), serializer.getTripleCount());
                }
            }

            @Override
            public void done() {
                synchronized (runCompletedEvent) {
                    db.finishRun(runner.getId());
                    runCompletedEvent.notifyAll();
                }
            }
        });
    }

    @Override
    public void run() {
        final Uploader uploader =
                new Uploader(config, db, this.zenodo, this.zenodoSandbox, runCompletedEvent);
        final Thread uploadThread = new Thread(uploader);
        uploadThread.start();

        try {
            while (true) {
                processDumps();
            }
        } catch(InterruptedException ignored) {
        } finally {
            uploadThread.interrupt();
            try {
                uploadThread.join();
            } catch(InterruptedException ignored) {}
        }
    }

    private static Backend create(Config config) throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setMaxLifetime(config.databaseMaxLifetime().toMillis());
        hikariConfig.setJdbcUrl(config.databaseAddress().toString());
        final DataSource dataSource = new HikariDataSource(hikariConfig);

        final CloseableHttpClient http = HttpClientBuilder.create().build();
        final Optional<ZenodoApi> zenodo = config.zenodoReleaseToken().map(token ->
                new ZenodoApi(http, ZenodoApi.MAIN_URI, token));
        final Optional<ZenodoApi> zenodoSandbox = config.zenodoSandboxToken().map(token ->
                new ZenodoApi(http, ZenodoApi.SANDBOX_URI, token));
        final BuildConfig buildConfig = BuildConfig.retrieve();

        if (zenodo.isEmpty()) {
            throw new RuntimeException("backend requires a token for release zenodo instance");
        }

        if (zenodoSandbox.isEmpty()) {
            throw new RuntimeException("backend requires a token for sandbox zenodo instance");
        }

        return new Backend(config, new Database(config, buildConfig, dataSource), zenodo.get(), zenodoSandbox.get());
    }


    public static void main(String[] args) {
        final BuildConfig buildConfig = BuildConfig.retrieve();
        System.err.println("Backend version " + buildConfig.toolVersion() + " with WDTK version " + buildConfig.wdtkVersion());

        int exitCode;
        try (Backend app = Backend.create(new ConfigEnv())) {
            exitCode = new CommandLine(app).execute(args);
        } catch(SQLException e) {
            exitCode = 1;
            System.err.println("initialization failed: " + e.toString());
            e.printStackTrace();
        }

        // make sure that when the main thread exits, the process exits
        // this is important for reliability: if the main process ends up here, then it's not in a healthy state.
        // exiting allows the process manager to notice that and restart the process.
        System.exit(exitCode);
    }
}

