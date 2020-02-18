package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.jdbc.MysqlDataSource;
import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.database.DumpTask;
import io.github.bennofs.wdumper.database.RunTask;
import io.github.bennofs.wdumper.ext.ZstdDumpFile;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.processors.FilteredRdfSerializer;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.zenodo.ZenodoApi;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import picocli.CommandLine;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

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

    private final Database db;
    private final ZenodoApi zenodo;
    private final ZenodoApi zenodoSandbox;
    final Object runCompletedEvent;

    private Backend(Database db, ZenodoApi zenodo, ZenodoApi zenodoSandbox) {
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
        final DumpRunner runner = DumpRunner.create(runId, dumpFile, outputDirectory);

        final ObjectMapper mapper = new ObjectMapper();
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
        if (!maybeRunTask.isPresent()) {
            Thread.sleep(Config.DUMP_INTERVAL_MILLIS);
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
                new Uploader(db, this.zenodo, this.zenodoSandbox, outputDirectory, runCompletedEvent);
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

    private static Backend create(final String dbUri, final String zenodoToken, final String zenodoSandboxToken) throws SQLException {
        final MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(dbUri);
        dataSource.setServerTimezone("UTC");

        final CloseableHttpClient http = HttpClientBuilder.create().build();
        final ZenodoApi zenodo = new ZenodoApi(http, ZenodoApi.MAIN_URI, zenodoToken);
        final ZenodoApi zenodoSandbox = new ZenodoApi(http, ZenodoApi.SANDBOX_URI, zenodoSandboxToken);

        return new Backend(new Database(dataSource), zenodo, zenodoSandbox);
    }


    public static void main(String[] args) {
        System.err.println("Backend version " + Config.TOOL_VERSION + " with WDTK version " + Config.WDTK_VERSION);

        final String dbUri = Config.constructDBUri();
        final String zenodoToken = System.getenv("ZENODO_TOKEN");
        final String zenodoSandboxToken = System.getenv("ZENODO_SANDBOX_TOKEN");

        try (Backend app = Backend.create(dbUri, zenodoToken, zenodoSandboxToken)) {
            new CommandLine(app).execute(args);
        } catch(SQLException e) {
            System.err.println("initialization failed: " + e.toString());
            e.printStackTrace();
        }
    }
}

