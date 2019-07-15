package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mysql.cj.jdbc.MysqlDataSource;
import io.github.bennofs.wdumper.ext.ZstdDumpFile;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import io.github.bennofs.wdumper.spec.DumpSpec;
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

public class App implements Runnable, Closeable {
    public static class DumpTask {
        public int id;
        public String spec;
    }

    @CommandLine.Parameters(paramLabel = "DUMP", arity = "1", index = "0", description = "JSON dump from wikidata to process")
    private Path dumpFilePath;

    @CommandLine.Option(names = {"-d", "--dump-dir"}, paramLabel = "DIR", description = "directory where the generated dumps are stored", defaultValue = "dumpfiles/generated")
    private Path outputDirectory;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
    boolean usageHelpRequested;

    final Jdbi jdbi;

    public App(DataSource dataSource) throws SQLException {
        this.jdbi = Jdbi.create(dataSource);

        this.jdbi.registerRowMapper(FieldMapper.factory(DumpTask.class));
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

    private void setProgress(Handle handle, int runId, int count) {
        handle.createUpdate("UPDATE run SET count = :count WHERE id = :id")
                .bind("id", runId)
                .bind("count", count)
                .execute();
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

    @Override
    public void run() {
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
                    Thread.sleep(30 * 1000);
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
                        jdbi.useHandle(handle -> finishRun(handle, runner.getId()));
                    }
                });
            }
        } catch(InterruptedException ignored) {
        }
    }

    private static App create(final String dbUri) throws SQLException {
        final MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(dbUri);
        dataSource.setServerTimezone("UTC");

        return new App(dataSource);
    }


    static final private String DATABASE_URI_DEFAULT = "jdbc:mysql://localhost/wdumper?user=root&password=";

    public static void main(String[] args) throws IOException {
        final String dbUri = ObjectUtils.defaultIfNull(System.getenv("DATABASE_URI"), DATABASE_URI_DEFAULT);

        try (App app = App.create(dbUri)) {
            new CommandLine(app).execute(args);
        } catch(SQLException e) {
            System.err.println("initialization failed: " + e.toString());
            e.printStackTrace();
        }
    }
}

