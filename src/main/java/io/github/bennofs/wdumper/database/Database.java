package io.github.bennofs.wdumper.database;

import io.github.bennofs.wdumper.Config;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.jooq.enums.DumpErrorLevel;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.bennofs.wdumper.jooq.tables.Dump.DUMP;
import static io.github.bennofs.wdumper.jooq.tables.DumpError.DUMP_ERROR;
import static io.github.bennofs.wdumper.jooq.tables.Run.RUN;
import static io.github.bennofs.wdumper.jooq.tables.Zenodo.ZENODO;

/**
 * Interface to the database.
 */
public class Database {
    final private DataSource dataSource;

    public Database(DataSource source) {
        this.dataSource = source;
    }

    public DSLContext context() {
        return DSL.using(dataSource, SQLDialect.MARIADB);
    }

    public Optional<RunTask> createRun(String dumpVersion) {
        return context().connectionResult(conn -> {
            conn.setAutoCommit(false);
            final DSLContext transaction = DSL.using(conn, SQLDialect.MARIADB);

            // we use a 3 step process here to ensure that we atomically claim a set of runs
            // to do so, we first create a unique "token" (run id) and then assign runs to that token
            // at the end, we then collect all the runs that were assigned this token
            final int runId = transaction
                    .insertInto(RUN, RUN.TOOL_VERSION, RUN.WDTK_VERSION, RUN.DUMP_DATE)
                    .values(Config.TOOL_VERSION, Config.WDTK_VERSION, dumpVersion)
                    .returning(RUN.ID)
                    .fetchOne()
                    .into(int.class);

            // define a window: only assign dumps if all dumps are older than ageTop, or at least one dump is older
            // than ageBottom
            final Field<LocalDateTime> ageTop = DSL.localDateTimeSub(DSL.currentLocalDateTime(), Config.RECENT_MIN_MINUTES, DatePart.MINUTE);
            final Field<LocalDateTime> ageBottom = DSL.localDateTimeSub(DSL.currentLocalDateTime(), Config.RECENT_MAX_MINUTES, DatePart.MINUTE);
            final Condition debounceCond = DSL.or(
                    DSL.max(DUMP.CREATED_AT).lt(ageTop),
                    DSL.min(DUMP.CREATED_AT).lt(ageBottom)
            );

            // claim the dumps
            final int updatedCount = transaction
                    .update(DUMP)
                    .set(DUMP.RUN_ID, runId)
                    .where(DUMP.RUN_ID.isNull())
                    .and(transaction.select(DSL.field(debounceCond)).asField())
                    .execute();
            if (updatedCount == 0) {
                conn.rollback();
                return Optional.empty();
            }

            // fetch the claimed dumps
            final List<DumpTask> tasks = transaction.select(DUMP.ID, DUMP.SPEC)
                    .where(DUMP.RUN_ID.eq(runId))
                    .stream()
                    .map(f -> new DumpTask(f.value1(), f.value2()))
                    .collect(Collectors.toList());
            conn.commit();

            return Optional.of(new RunTask(runId, tasks));
        });
    }

    public void startRun(int runId) {
        context().update(RUN)
                .set(RUN.STARTED_AT, DSL.currentLocalDateTime())
                .where(RUN.ID.eq(runId))
                .execute();
    }

    public void finishRun(int runId) {
        context().update(RUN)
                .set(RUN.FINISHED_AT, DSL.currentLocalDateTime())
                .where(RUN.ID.eq(runId))
                .execute();
    }

    public void logDumpMessage(int runId, int dumpId, DumpStatusHandler.ErrorLevel level, String message) {
        context().insertInto(DUMP_ERROR)
                .set(DUMP_ERROR.LOGGED_AT, DSL.currentLocalDateTime())
                .set(DUMP_ERROR.RUN_ID, runId)
                .set(DUMP_ERROR.DUMP_ID, dumpId)
                .set(DUMP_ERROR.LEVEL, DumpErrorLevel.valueOf(level.toString()))
                .set(DUMP_ERROR.MESSAGE, message)
                .execute();
    }

    public void logUploadMessage(int dumpId, int zenodoId, DumpStatusHandler.ErrorLevel level, String message) {
        context().insertInto(DUMP_ERROR)
                .set(DUMP_ERROR.LOGGED_AT, DSL.currentLocalDateTime())
                .set(DUMP_ERROR.RUN_ID, DSL.val((Integer)null))
                .set(DUMP_ERROR.DUMP_ID, dumpId)
                .set(DUMP_ERROR.ZENODO_ID, zenodoId)
                .set(DUMP_ERROR.LEVEL, DumpErrorLevel.valueOf(level.toString()))
                .set(DUMP_ERROR.MESSAGE, message)
                .execute();
    }

    public void setProgress(int runId, int count) {
        context().update(RUN)
                .set(RUN.COUNT, count)
                .where(RUN.ID.eq(runId))
                .execute();
    }

    public void setDumpStatistics(int dumpId, long entityCount, long statementCount, long tripleCount) {
        context().update(DUMP)
                .set(DUMP.ENTITY_COUNT, entityCount)
                .set(DUMP.STATEMENT_COUNT, statementCount)
                .set(DUMP.TRIPLE_COUNT, tripleCount)
                .where(DUMP.ID.eq(dumpId))
                .execute();
    }

    public List<ZenodoTask> getZenodoTasks(int amount) {
        // find tasks
        final Stream<ZenodoTask> tasks = context().select(ZENODO.ID, ZENODO.DEPOSIT_ID, ZENODO.DUMP_ID, ZENODO.TARGET)
                .from(ZENODO)
                .innerJoin(DUMP).on(ZENODO.DUMP_ID.eq(DUMP.ID))
                .innerJoin(RUN).on(DUMP.RUN_ID.eq(RUN.ID))
                .where(ZENODO.STARTED_AT.isNull())
                .and(RUN.FINISHED_AT.isNotNull())
                .stream()
                .map(r -> new ZenodoTask(r.value1(), r.value2(), r.value3(), r.value4().toString()));

        return tasks.filter(task ->
                // update started field, while checking that no one else has started this upload yet
                context().update(ZENODO)
                    .set(ZENODO.STARTED_AT, DSL.currentLocalDateTime())
                    .where(ZENODO.ID.eq(task.id))
                    .and(ZENODO.STARTED_AT.isNull())
                    .execute() == 1
        ).limit(amount).collect(Collectors.toList());
    }

    public void setUploadFinished(int id) {
        context().update(ZENODO)
                .set(ZENODO.COMPLETED_AT, DSL.currentLocalDateTime())
                .where(ZENODO.ID.eq(id))
                .execute();
    }

    public void setUploadProgress(int id, long uploadedBytes) {
        context().update(ZENODO)
                .set(ZENODO.UPLOADED_BYTES, uploadedBytes)
                .where(ZENODO.ID.eq(id))
                .execute();
    }

    public String getDumpSpec(int id) {
        return context().select(DUMP.SPEC).where(DUMP.ID.eq(id)).fetchOne().value1();
    }

    public DumpInfo getDumpInfo(int id) {
        return context().select(DUMP.ID, RUN.WDTK_VERSION, RUN.TOOL_VERSION, RUN.DUMP_DATE, DUMP.TRIPLE_COUNT, DUMP.ENTITY_COUNT, DUMP.STATEMENT_COUNT)
                .from(DUMP).innerJoin(RUN).on(DUMP.RUN_ID.eq(RUN.ID))
                .where(DUMP.ID.eq(id))
                .fetchOne()
                .into(DumpInfo.class);
    }

}
