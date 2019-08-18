package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.MoreObjects;
import com.mysql.cj.jdbc.MysqlDataSource;
import io.github.bennofs.wdumper.ext.ZstdDumpFile;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.spec.DumpSpec;
import io.github.bennofs.wdumper.zenodo.Deposit;
import io.github.bennofs.wdumper.zenodo.Zenodo;
import kong.unirest.ProgressMonitor;
import org.apache.commons.lang3.ObjectUtils;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.FieldMapper;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import picocli.CommandLine;

import javax.sql.DataSource;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class App implements Runnable, Closeable {
    public static class DumpTask {
        public int id;
        public String spec;
    }

    public static class ZenodoTask {
        public int id;
        public int deposit_id;
        public int dump_id;
        public String target;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("id", id)
                    .add("deposit_id", deposit_id)
                    .add("dump_id", dump_id)
                    .add("target", target)
                    .toString();
        }
    }

    // time to wait between checks for new upload tasks
    private static final int UPLOAD_INTERVAL_MILLIS = 10000;

    // time to wait between checks for new dump tasks
    private static final int DUMP_INTERVAL_MILLIS = 5 * 60 * 1000;

    // unique ID for this worker
    private static final int WORKER_ID = 1;

    @CommandLine.Parameters(paramLabel = "DUMP", arity = "1", index = "0", description = "JSON dump from wikidata to process")
    private Path dumpFilePath;

    @CommandLine.Option(names = {"-d", "--dump-dir"}, paramLabel = "DIR", description = "directory where the generated dumps are stored", defaultValue = "dumpfiles/generated")
    private Path outputDirectory;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean usageHelpRequested;

    private final Jdbi jdbi;
    private final Zenodo zenodo;
    private final Zenodo zenodoSandbox;
    private final Object runCompletedEvent;

    private App(DataSource dataSource, Zenodo zenodo, Zenodo zenodoSandbox) throws SQLException {
        this.jdbi = Jdbi.create(dataSource);

        this.jdbi.registerRowMapper(FieldMapper.factory(DumpTask.class));
        this.jdbi.registerRowMapper(FieldMapper.factory(ZenodoTask.class));

        this.zenodo = zenodo;
        this.zenodoSandbox = zenodoSandbox;

        this.runCompletedEvent = new Object();
    }


    @Override
    public void close() {
    }

    private int createRun(Handle handle) {
        return handle.createUpdate("INSERT INTO run () VALUES ()")
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Integer.class)
                .one();
    }

    private boolean claimDumps(Handle handle, int runId) {
        return handle
                .createUpdate("UPDATE dump SET run_id = :run WHERE run_id IS NULL")
                .bind("run", runId)
                .execute() != 0;
    }

    private List<DumpTask> fetchDumpTasks(Handle handle, int runId) {
        return handle
                .createQuery("SELECT id, spec FROM dump WHERE run_id = :run")
                .bind("run", runId)
                .mapTo(DumpTask.class)
                .list();
    }

    private void startRun(Handle handle, int runId) {
        handle.createUpdate("UPDATE run SET started_at = NOW() WHERE id = :id")
                .bind("id", runId)
                .execute();
    }

    private void finishRun(Handle handle, int runId) {
        handle.createUpdate("UPDATE run SET finished_at = NOW() WHERE id = :id")
                .bind("id", runId)
                .execute();
    }

    private void logDumpMessage(Handle handle, int runId, int dumpId, DumpStatusHandler.ErrorLevel level, String message) {
        handle.createUpdate("INSERT INTO dump_error (logged_at, run_id, dump_id, level, message) VALUES (NOW(), :run, :dump, :level, :message)")
                .bind("run", runId)
                .bind("dump", dumpId)
                .bind("level", level.toString())
                .bind("message", message)
                .execute();
    }

    private void logUploadMessage(Handle handle, int dumpId, int zenodoId, DumpStatusHandler.ErrorLevel level, String message) {
        handle.createUpdate("INSERT INTO dump_error (logged_at, dump_id, run_id, zenodo_id, level, message) VALUES (NOW(), :dump, NULL, :zenodo, :level, :message)")
                .bind("dump", dumpId)
                .bind("zenodo", zenodoId)
                .bind("level", level.toString())
                .bind("message", message)
                .execute();
    }

    private void setProgress(Handle handle, int runId, int count) {
        handle.createUpdate("UPDATE run SET count = :count WHERE id = :id")
                .bind("id", runId)
                .bind("count", count)
                .execute();
    }

    private List<ZenodoTask> getZenodoTasks(Handle handle, int amount) {
        // find tasks
        final Stream<ZenodoTask> tasks = handle.createQuery("SELECT zenodo.id, zenodo.deposit_id, zenodo.dump_id, zenodo.target " +
                "FROM zenodo INNER JOIN dump ON zenodo.dump_id = dump.id INNER JOIN run ON dump.run_id = run.id " +
                "WHERE zenodo.started_at IS NULL AND run.finished_at IS NOT NULL")
                .mapTo(ZenodoTask.class)
                .stream();

        return tasks.filter(task ->
                // update started field, while checking that no one else has started this upload yet
                handle.createUpdate("UPDATE zenodo SET started_at = NOW() WHERE id = :id AND started_at IS NULL")
                        .bind("id", task.id)
                        .execute() == 1
        ).limit(amount).collect(Collectors.toList());
    }

    private void setUploadFinished(Handle handle, int id) {
        handle.createUpdate("UPDATE zenodo SET completed_at = NOW() WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    private String getDumpSpec(Handle handle, int id) {
        return handle.createQuery("SELECT spec FROM dump WHERE id = :id")
                .bind("id", id)
                .mapTo(String.class)
                .one();
    }

    private MwDumpFile openDumpFile() {
        return new ZstdDumpFile(this.dumpFilePath.toAbsolutePath().toString());
    }

    private DumpRunner createRunner(Handle handle, int runId) {
        final DumpRunner runner = DumpRunner.create(runId, openDumpFile(), outputDirectory);

        List<DumpTask> tasks = fetchDumpTasks(handle, runId);
        final ObjectMapper mapper = new ObjectMapper();
        for (DumpTask task : tasks) {
            try {
                runner.addDumpTask(task.id, mapper.readValue(task.spec, DumpSpec.class), new DumpStatusHandler() {
                    @Override
                    public void reportError(ErrorLevel level, String message) {
                        jdbi.useHandle(h -> logDumpMessage(h, runId, task.id, level, message));
                    }
                });
            } catch(IOException e) {
                jdbi.useHandle(h ->
                        logDumpMessage(h, runId, task.id, DumpStatusHandler.ErrorLevel.CRITICAL, "initialization failed: " + e.toString())
                );
                e.printStackTrace();
            }
        }

        return runner;
    }

    static class UploadProgressMonitor implements ProgressMonitor, Closeable {
        private final Jdbi jdbi;
        private final int id;
        private long progress;
        private final Timer timer;

        public UploadProgressMonitor(Jdbi jdbi, final int id) {
            this.jdbi = jdbi;
            this.id = id;
            this.progress = 0;
            this.timer = new Timer();
            this.timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    try {
                        jdbi.useHandle(handle -> setUploadProgress(handle, id, progress));
                    } catch(Exception e) {
                        System.out.println("error while updating upload progress, ignored");
                        e.printStackTrace();
                    }
                }
            }, 0, 1000 * 20);
        }

        @Override
        public void accept(String field, String fileName, Long bytesWritten, Long totalBytes) {
            this.progress = bytesWritten;
        }

        private void setUploadProgress(Handle handle, int id, long uploadedBytes) {
            handle.createUpdate("UPDATE zenodo SET uploaded_bytes := :bytes WHERE id = :id")
                    .bind("id", id)
                    .bind("bytes", uploadedBytes)
                    .execute();
        }

        public void close() {
            jdbi.useHandle(handle -> setUploadProgress(handle, this.id, progress));
            this.timer.cancel();
        }
    }

    private void uploadLoop() {
        try {
            while (true) {
                final List<ZenodoTask> tasks;
                synchronized (this.runCompletedEvent) {
                    tasks = this.jdbi.withHandle(handle -> getZenodoTasks(handle, 1));
                    if (tasks.isEmpty()) {
                        this.runCompletedEvent.wait(UPLOAD_INTERVAL_MILLIS);
                        continue;
                    }
                }

                for (ZenodoTask task : tasks) {
                    try {
                        System.out.println("starting upload: " + task.toString());

                        final Zenodo api = task.target.equals("RELEASE") ? this.zenodo : this.zenodoSandbox;
                        final Path outputPath = DumpRunner.getOutputPath(outputDirectory, task.dump_id);

                        final Deposit deposit = api.getDeposit(task.deposit_id);

                        System.out.println(deposit.toString());
                        Thread.sleep(1000);

                        final String dumpSpec = jdbi.withHandle(handle -> getDumpSpec(handle, task.dump_id));
                        deposit.addFile("wdumper-spec.json", dumpSpec, (field, fileName, bytesWritten, totalBytes) -> {
                        });

                        try (final UploadProgressMonitor progress = new UploadProgressMonitor(jdbi, task.id)) {
                            deposit.addFile(outputPath.getFileName().toString(), outputPath.toFile(), progress);
                        }
                        deposit.publish();

                        System.out.println("finished upload: " + task.toString());
                        jdbi.useHandle(handle -> setUploadFinished(handle, task.deposit_id));
                    } catch(Exception e) {
                        System.out.println("upload failed");
                        e.printStackTrace();
                        jdbi.useHandle(handle -> {
                            logUploadMessage(handle, task.dump_id, task.id, DumpStatusHandler.ErrorLevel.CRITICAL, e.toString());
                        });
                    }
                }
            }
        } catch(InterruptedException ignored) {
        }
    }

    @Override
    public void run() {
        final Thread uploadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                uploadLoop();
            }
        });
        uploadThread.start();

        try {
            while (true) {
                System.out.println("checking for new tasks");
                final DumpRunner runner = this.jdbi.withHandle(t -> t.inTransaction(handle -> {
                    final int runId = createRun(handle);

                    if (!claimDumps(handle, runId)) {
                        handle.rollback();
                        return null;
                    }

                    return createRunner(handle, runId);
                }));

                if (runner == null) {
                    Thread.sleep(DUMP_INTERVAL_MILLIS);
                    continue;
                }

                runner.run(new RunnerStatusHandler() {
                    @Override
                    public void start() {
                        jdbi.useHandle(handle -> startRun(handle, runner.getId()));
                    }

                    @Override
                    public void reportProgress(int count) {
                        jdbi.useHandle(handle -> setProgress(handle, runner.getId(), count));
                    }

                    @Override
                    public void done() {
                        synchronized (runCompletedEvent) {
                            jdbi.useHandle(handle -> finishRun(handle, runner.getId()));
                            runCompletedEvent.notifyAll();
                        }
                    }
                });
            }
        } catch(InterruptedException ignored) {
        } finally {
            uploadThread.interrupt();
        }
    }

    private static App create(final String dbUri, final String zenodoToken, final String zenodoSandboxToken) throws SQLException {
        final MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(dbUri);
        dataSource.setServerTimezone("UTC");

        final Zenodo zenodo = new Zenodo("https://zenodo.org/api/", zenodoToken);
        final Zenodo zenodoSandbox = new Zenodo("https://sandbox.zenodo.org/api/", zenodoSandboxToken);

        return new App(dataSource, zenodo, zenodoSandbox);
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

