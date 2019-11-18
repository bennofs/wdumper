package io.github.bennofs.wdumper.database;

import io.github.bennofs.wdumper.Constants;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.FieldMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Interface to the database.
 */
public class Database {
    final private Jdbi jdbi;

    public Database(DataSource source) {
        this.jdbi = Jdbi.create(source);

        this.jdbi.registerRowMapper(FieldMapper.factory(DumpTask.class));
        this.jdbi.registerRowMapper(FieldMapper.factory(ZenodoTask.class));
        this.jdbi.registerRowMapper(FieldMapper.factory(DumpInfo.class));
    }

    public Jdbi getJdbi() {
        return this.jdbi;
    }

    public <R, X extends Exception> R withHandle(HandleCallback<R, X> callback) throws X {
        return this.jdbi.withHandle(callback);
    }

    public <X extends Exception> void useHandle(final HandleConsumer<X> callback) throws X {
        this.jdbi.useHandle(callback);
    }

    public int createRun(Handle handle, String dumpVersion) {
        return handle.createUpdate("INSERT INTO run (tool_version, wdtk_version, dump_date) VALUES (:tool_version, :wdtk_version, :dump_date)")
                .bind("tool_version", Constants.TOOL_VERSION)
                .bind("wdtk_version", Constants.WDTK_VERSION)
                .bind("dump_date", dumpVersion)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Integer.class)
                .one();
    }

    public boolean claimDumps(Handle handle, int runId) {
        return handle
                .createUpdate("UPDATE dump SET run_id = :run WHERE run_id IS NULL " +
                        "AND (SELECT (MAX(created_at) < DATE_SUB(NOW(), INTERVAL :recent_min MINUTE)) OR (MIN(created_at) > DATE_SUB(NOW(), INTERVAL :recent_max MINUTE)))")
                .bind("run", runId)
                .bind("recent_min", Constants.RECENT_MIN_MINUTES)
                .bind("recent_max", Constants.RECENT_MAX_MINUTES)
                .execute() != 0;
    }

    public List<DumpTask> fetchDumpTasks(Handle handle, int runId) {
        return handle
                .createQuery("SELECT id, spec FROM dump WHERE run_id = :run")
                .bind("run", runId)
                .mapTo(DumpTask.class)
                .list();
    }

    public void startRun(Handle handle, int runId) {
        handle.createUpdate("UPDATE run SET started_at = NOW() WHERE id = :id")
                .bind("id", runId)
                .execute();
    }

    public void finishRun(Handle handle, int runId) {
        handle.createUpdate("UPDATE run SET finished_at = NOW() WHERE id = :id")
                .bind("id", runId)
                .execute();
    }

    public void logDumpMessage(Handle handle, int runId, int dumpId, DumpStatusHandler.ErrorLevel level, String message) {
        handle.createUpdate("INSERT INTO dump_error (logged_at, run_id, dump_id, level, message) VALUES (NOW(), :run, :dump, :level, :message)")
                .bind("run", runId)
                .bind("dump", dumpId)
                .bind("level", level.toString())
                .bind("message", message)
                .execute();
    }

    public void logUploadMessage(Handle handle, int dumpId, int zenodoId, DumpStatusHandler.ErrorLevel level, String message) {
        handle.createUpdate("INSERT INTO dump_error (logged_at, dump_id, run_id, zenodo_id, level, message) VALUES (NOW(), :dump, NULL, :zenodo, :level, :message)")
                .bind("dump", dumpId)
                .bind("zenodo", zenodoId)
                .bind("level", level.toString())
                .bind("message", message)
                .execute();
    }

    public void setProgress(Handle handle, int runId, int count) {
        handle.createUpdate("UPDATE run SET count = :count WHERE id = :id")
                .bind("id", runId)
                .bind("count", count)
                .execute();
    }

    public void setDumpStatistics(Handle handle, int dumpId, long entityCount, long statementCount, long tripleCount) {
        handle.createUpdate("UPDATE dump SET entity_count = :entity_count, statement_count = :statement_count, triple_count = :triple_count WHERE id = :id")
                .bind("id", dumpId)
                .bind("entity_count", entityCount)
                .bind("statement_count", statementCount)
                .bind("triple_count", tripleCount)
                .execute();
    }

    public List<ZenodoTask> getZenodoTasks(Handle handle, int amount) {
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

    public void setUploadFinished(Handle handle, int id) {
        handle.createUpdate("UPDATE zenodo SET completed_at = NOW() WHERE id = :id")
                .bind("id", id)
                .execute();
    }

    public void setUploadProgress(Handle handle, int id, long uploadedBytes) {
        handle.createUpdate("UPDATE zenodo SET uploaded_bytes := :bytes WHERE id = :id")
                .bind("id", id)
                .bind("bytes", uploadedBytes)
                .execute();
    }

    public String getDumpSpec(Handle handle, int id) {
        return handle.createQuery("SELECT spec FROM dump WHERE id = :id")
                .bind("id", id)
                .mapTo(String.class)
                .one();
    }

    public DumpInfo getDumpInfo(Handle handle, int id) {
        return handle.createQuery("SELECT dump.id, run.wdtk_version, run.tool_version, run.dump_date, triple_count, entity_count, statement_count " +
                "FROM dump INNER JOIN run ON run.id = dump.run_id WHERE dump.id = :id")
                .bind("id", id)
                .mapTo(DumpInfo.class)
                .one();
    }

}
