package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.jdbc.MysqlDataSource;
import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.database.DumpTask;
import io.github.bennofs.wdumper.database.ZenodoTask;
import io.github.bennofs.wdumper.ext.ZstdDumpFile;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.zenodo.Deposit;
import io.github.bennofs.wdumper.zenodo.Zenodo;
import org.apache.commons.lang3.ObjectUtils;
import org.jdbi.v3.core.Handle;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import picocli.CommandLine;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * The main class for the backend application.
 */
public class App implements Runnable, Closeable {

    @CommandLine.Parameters(paramLabel = "DUMP", arity = "1", index = "0", description = "JSON dump from wikidata to process")
    private Path dumpFilePath;

    @CommandLine.Option(names = {"-d", "--dump-dir"}, paramLabel = "DIR", description = "directory where the generated dumps are stored", defaultValue = "dumpfiles/generated")
    private Path outputDirectory;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean usageHelpRequested;

    private final Database db;
    private final Uploader uploader;
    final Object runCompletedEvent;

    private App(Database db, Zenodo zenodo, Zenodo zenodoSandbox) {
        this.db = db;
        this.runCompletedEvent = new Object();
        this.uploader = new Uploader(db, zenodo, zenodoSandbox, outputDirectory, runCompletedEvent);
    }

    @Override
    public void close() {
    }

    private MwDumpFile openDumpFile() {
        return new ZstdDumpFile(this.dumpFilePath.toAbsolutePath().toString());
    }

    private DumpRunner createRunner(Handle handle, int runId) {
        final DumpRunner runner = DumpRunner.create(runId, openDumpFile(), outputDirectory);

        List<DumpTask> tasks = db.fetchDumpTasks(handle, runId);
        final ObjectMapper mapper = new ObjectMapper();
        for (DumpTask task : tasks) {
            try {
                runner.addDumpTask(task.id, mapper.readValue(task.spec, DumpSpec.class), new DumpStatusHandler() {
                    @Override
                    public void reportError(ErrorLevel level, String message) {
                        db.useHandle(h -> db.logDumpMessage(h, runId, task.id, level, message));
                    }
                });
            } catch(IOException e) {
                db.useHandle(h ->
                        db.logDumpMessage(h, runId, task.id, DumpStatusHandler.ErrorLevel.CRITICAL, "initialization failed: " + e.toString())
                );
                e.printStackTrace();
            }
        }

        return runner;
    }

    private void processDumps() throws InterruptedException {
        System.out.println("checking for new tasks");
        final DumpRunner runner = this.db.withHandle(t -> t.inTransaction(handle -> {
            final int runId = db.createRun(handle);

            if (!db.claimDumps(handle, runId)) {
                handle.rollback();
                return null;
            }

            return createRunner(handle, runId);
        }));

        // no new tasks
        if (runner == null) {
            Thread.sleep(Constants.DUMP_INTERVAL_MILLIS);
            return;
        }

        runner.run(new RunnerStatusHandler() {
            @Override
            public void start() {
                db.useHandle(handle -> db.startRun(handle, runner.getId()));
            }

            @Override
            public void reportProgress(int count) {
                db.useHandle(handle -> db.setProgress(handle, runner.getId(), count));
            }

            @Override
            public void done() {
                synchronized (runCompletedEvent) {
                    db.useHandle(handle -> db.finishRun(handle, runner.getId()));
                    runCompletedEvent.notifyAll();
                }
            }
        });
    }

    @Override
    public void run() {
        final Thread uploadThread = new Thread(this.uploader);
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

    private static App create(final String dbUri, final String zenodoToken, final String zenodoSandboxToken) throws SQLException {
        final MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(dbUri);
        dataSource.setServerTimezone("UTC");

        final Zenodo zenodo = new Zenodo("https://zenodo.org/api/", zenodoToken);
        final Zenodo zenodoSandbox = new Zenodo("https://sandbox.zenodo.org/api/", zenodoSandboxToken);

        return new App(new Database(dataSource), zenodo, zenodoSandbox);
    }


    public static void main(String[] args) throws IOException {
        final String dbHost = ObjectUtils.defaultIfNull(System.getenv("DB_HOST"), "localhost");
        final String dbName = ObjectUtils.defaultIfNull(System.getenv("DB_NAME"), "wdumper");
        final String dbUser = ObjectUtils.defaultIfNull(System.getenv("DB_USER"), "root");
        final String dbPassword = ObjectUtils.defaultIfNull(System.getenv("DB_PASSWORD"), "");

        final String dbUri = "jdbc:mysql://" + dbHost + "/" + dbName + "?sslMode=DISABLED&user=" + dbUser + "&password=" + dbPassword;
        final String zenodoToken = System.getenv("ZENODO_TOKEN");
        final String zenodoSandboxToken = System.getenv("ZENODO_SANDBOX_TOKEN");


        try (App app = App.create(dbUri, zenodoToken, zenodoSandboxToken)) {
            new CommandLine(app).execute(args);
        } catch(SQLException e) {
            System.err.println("initialization failed: " + e.toString());
            e.printStackTrace();
        }
    }
}

