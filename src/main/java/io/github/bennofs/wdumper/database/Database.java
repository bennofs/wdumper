package io.github.bennofs.wdumper.database;

import io.github.bennofs.wdumper.BuildConfig;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.jooq.enums.DB_DumpErrorLevel;
import io.github.bennofs.wdumper.jooq.enums.DB_ZenodoTarget;
import io.github.bennofs.wdumper.jooq.tables.DB_Zenodo;
import io.github.bennofs.wdumper.jooq.tables.records.DB_DumpErrorRecord;
import io.github.bennofs.wdumper.jooq.tables.records.DB_DumpRecord;
import io.github.bennofs.wdumper.jooq.tables.records.DB_RunRecord;
import io.github.bennofs.wdumper.jooq.tables.records.DB_ZenodoRecord;
import io.github.bennofs.wdumper.model.*;
import org.apache.commons.lang3.Range;
import org.jooq.*;
import org.jooq.impl.DSL;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.bennofs.wdumper.jooq.Tables.*;

/**
 * Interface to the database.
 */
public class Database {
    final private DataSource dataSource;
    final private BuildConfig buildConfig;
    final private Config config;

    /**
     * For docs, see implementation at {@link io.github.bennofs.wdumper.Config}
     */
    public interface Config {
        Duration minDumpRequestAge();

        Duration maxDumpRequestAge();
    }

    public Database(Config config, BuildConfig buildConfig, DataSource source) {
        this.config = config;
        this.buildConfig = buildConfig;
        this.dataSource = source;
    }

    public DSLContext context() {
        return DSL.using(dataSource, SQLDialect.MARIADB);
    }

    public Optional<RunTask> createRun(String dumpVersion) {
        return context().connectionResult(conn -> {
            conn.setAutoCommit(false);
            final DSLContext transaction = DSL.using(conn, SQLDialect.MARIADB);

            // we use a 3 step process here to ensure that we atomically claim a set of dumps
            // to do so, we first create a unique "token" (run id) and then assign runs to that token
            // at the end, we then collect all the runs that were assigned this token
            final int runId = transaction
                    .insertInto(RUN, RUN.TOOL_VERSION, RUN.WDTK_VERSION, RUN.DUMP_DATE)
                    .values(buildConfig.toolVersion(), buildConfig.wdtkVersion(), dumpVersion)
                    .returningResult(RUN.ID)
                    .fetchOne()
                    .into(int.class);

            // define a window: only assign dumps if all dumps are older than ageTop, or at least one dump is older
            // than ageBottom
            final Field<LocalDateTime> ageTop = DSL.localDateTimeSub(DSL.currentLocalDateTime(), config.minDumpRequestAge().toSeconds(), DatePart.SECOND);
            final Field<LocalDateTime> ageBottom = DSL.localDateTimeSub(DSL.currentLocalDateTime(), config.maxDumpRequestAge().toSeconds(), DatePart.SECOND);
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
                    .from(DUMP)
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
                .set(DUMP_ERROR.LEVEL, DB_DumpErrorLevel.valueOf(level.toString()))
                .set(DUMP_ERROR.MESSAGE, message)
                .execute();
    }

    public void logUploadMessage(int dumpId, int zenodoId, DumpStatusHandler.ErrorLevel level, String message) {
        context().insertInto(DUMP_ERROR)
                .set(DUMP_ERROR.LOGGED_AT, DSL.currentLocalDateTime())
                .set(DUMP_ERROR.RUN_ID, DSL.val((Integer) null))
                .set(DUMP_ERROR.DUMP_ID, dumpId)
                .set(DUMP_ERROR.ZENODO_ID, zenodoId)
                .set(DUMP_ERROR.LEVEL, DB_DumpErrorLevel.valueOf(level.toString()))
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

    private static Dump recordToDump(DB_DumpRecord record) {
        if (record.get(DUMP.ID) == null) return null;

        return Dump.builder()
                .id(record.get(DUMP.ID))
                .createdAt(record.get(DUMP.CREATED_AT).toInstant(ZoneOffset.UTC))
                .spec(record.get(DUMP.SPEC))
                .runId(record.get(DUMP.RUN_ID))
                .title(record.get(DUMP.TITLE))
                .description(record.get(DUMP.DESCRIPTION))
                .compressedSize(record.get(DUMP.COMPRESSED_SIZE))
                .tripleCount(record.get(DUMP.TRIPLE_COUNT))
                .entityCount(record.get(DUMP.ENTITY_COUNT))
                .statementCount(record.get(DUMP.STATEMENT_COUNT))
                .build();
    }

    private static Run recordToRun(DB_RunRecord record) {
        if (record.get(RUN.ID) == null) return null;

        final Run.Builder builder = Run.builder()
                .id(record.get(RUN.ID))
                .count(record.get(RUN.COUNT))
                .dumpDate(record.get(RUN.DUMP_DATE))
                .toolVersion(record.get(RUN.TOOL_VERSION))
                .wdtkVersion(record.get(RUN.WDTK_VERSION));

        final LocalDateTime startedAt = record.get(RUN.STARTED_AT);
        if (startedAt != null) {
            builder.startedAt(startedAt.toInstant(ZoneOffset.UTC));
        }

        final LocalDateTime finishedAt = record.get(RUN.FINISHED_AT);
        if (finishedAt != null) {
            builder.finishedAt(finishedAt.toInstant(ZoneOffset.UTC));
        }

        return builder.build();
    }

    private static DumpError recordToDumpError(DB_DumpErrorRecord record) {
        if (record.get(DUMP_ERROR.ID) == null) return null;

        return DumpError.builder()
                .id(record.get(DUMP_ERROR.ID))
                .dumpId(Optional.ofNullable(record.get(DUMP_ERROR.DUMP_ID)))
                .runId(Optional.ofNullable(record.get(DUMP_ERROR.RUN_ID)))
                .zenodoId(Optional.ofNullable(record.get(DUMP_ERROR.ZENODO_ID)))
                .level(record.get(DUMP_ERROR.LEVEL, DumpError.Level.class))
                .loggedAt(record.get(DUMP_ERROR.LOGGED_AT).toInstant(ZoneOffset.UTC))
                .message(record.get(DUMP_ERROR.MESSAGE))
                .build();
    }

    private static Zenodo recordToZenodo(DB_ZenodoRecord record) {
        if (record.get(ZENODO.ID) == null) return null;

        Zenodo.Builder builder = Zenodo.builder()
                .id(record.get(ZENODO.ID))
                .createdAt(record.get(ZENODO.CREATED_AT).toInstant(ZoneOffset.UTC))
                .depositId(record.get(ZENODO.DEPOSIT_ID))
                .doi(record.get(ZENODO.DOI))
                .dumpId(record.get(ZENODO.DUMP_ID))
                .target(record.get(ZENODO.TARGET, Zenodo.Target.class))
                .uploadedBytes(record.get(ZENODO.UPLOADED_BYTES));

        final LocalDateTime started = record.get(ZENODO.STARTED_AT);
        if (started != null) {
            builder.startedAt(started.toInstant(ZoneOffset.UTC));
        }

        final LocalDateTime completed = record.get(ZENODO.COMPLETED_AT);
        if (completed != null) {
            builder.completedAt(completed.toInstant(ZoneOffset.UTC));
        }

        return builder.build();
    }

    public Optional<Run> getRun(int id) {
        return context()
                .selectFrom(RUN)
                .where(RUN.ID.eq(id))
                .fetchOptional()
                .map(Database::recordToRun);
    }

    public Optional<Dump> getDump(int id) {
        return context()
                .selectFrom(DUMP)
                .where(DUMP.ID.eq(id))
                .fetchOptional()
                .map(Database::recordToDump);
    }


    private final static DB_Zenodo ZENODO_SANDBOX = ZENODO.as("zenodoSandbox");
    private final static DB_Zenodo ZENODO_RELEASE = ZENODO.as("zenodoRelease");
    private final static Table<Record> DUMP_RUN_ZENODO = DUMP
            .leftOuterJoin(RUN.as("run")).on(RUN.ID.eq(DUMP.RUN_ID))
            .leftOuterJoin(ZENODO_SANDBOX).on(ZENODO_SANDBOX.DUMP_ID.eq(DUMP.ID)
                    .and(ZENODO_SANDBOX.TARGET.eq(DB_ZenodoTarget.SANDBOX)))
            .leftOuterJoin(ZENODO_RELEASE).on(ZENODO_RELEASE.DUMP_ID.eq(DUMP.ID)
                    .and(ZENODO_RELEASE.TARGET.eq(DB_ZenodoTarget.RELEASE)));

    private static DumpRunZenodo recordToDumpRunZenodo(Record record) {
        return new DumpRunZenodo(
                recordToDump(record.into(DUMP)),
                recordToRun(record.into(RUN)),
                recordToZenodo(record.into(ZENODO_SANDBOX)),
                recordToZenodo(record.into(ZENODO_RELEASE))
        );
    }

    public Optional<DumpFullInfo> getDumpWithFullInfo(int id) {
        final Record dumpRunRecord = context()
                .selectFrom(DUMP_RUN_ZENODO)
                .where(DUMP.ID.eq(id))
                .fetchOne();
        if (dumpRunRecord == null) return Optional.empty();

        final Dump dump = recordToDump(dumpRunRecord.into(DUMP));
        final Run run = recordToRun(dumpRunRecord.into(RUN));
        final Zenodo zenodoSandbox = recordToZenodo(dumpRunRecord.into(ZENODO_SANDBOX));
        final Zenodo zenodoRelease = recordToZenodo(dumpRunRecord.into(ZENODO_RELEASE));

        assert dump != null : "SQL query should ensure dump cannot be null";
        Condition relatedError = DUMP_ERROR.DUMP_ID.eq(dump.id());
        if (run != null) {
            relatedError = relatedError.or(DUMP_ERROR.RUN_ID.eq(run.id()));
        }
        if (zenodoSandbox != null) {
            relatedError = relatedError.or(DUMP_ERROR.ZENODO_ID.eq(zenodoSandbox.id()));
        }
        if (zenodoRelease != null) {
            relatedError = relatedError.or(DUMP_ERROR.ZENODO_ID.eq(zenodoRelease.id()));
        }

        final List<DumpError> errors = context()
                .selectFrom(DUMP_ERROR)
                .where(relatedError)
                .fetch()
                .stream()
                .map(Database::recordToDumpError)
                .collect(Collectors.toList());

        return Optional.of(new DumpFullInfo(dump, run, zenodoSandbox, zenodoRelease, errors));
    }

    public Optional<Range<Integer>> getAllDumpsRange() {
        final Field<Integer> maxField = DSL.max(DUMP.ID);
        final Field<Integer> minField = DSL.min(DUMP.ID);

        final Optional<Record2<Integer, Integer>> result = context().select(DSL.max(DUMP.ID), DSL.min(DUMP.ID))
                .from(DUMP)
                .having(maxField.isNotNull().and(minField.isNotNull()))
                .fetchOptional();

        return result.map(record -> Range.between(record.get(minField), record.get(maxField)));
    }

    /**
     * Get recent dumps created after some given id.
     *
     * @param before only dumps created after (i.e. that are more recent) than the dump with this id are returned
     * @param limit  number of dumps to return
     * @param extend if true, move {@code before} to a less recent dump in order to return at least {@code limit} items.
     * @return list of dumps, sorted such that the most recent dump is first
     */
    public List<DumpRunZenodo> getRecentDumpsPrev(@Nullable Integer before, Integer limit, boolean extend) {
        Collection<Condition> conditions = Collections.emptyList();
        if (before != null) {
            conditions = Collections.singleton(DUMP.ID.gt(before));
        }

        final List<DumpRunZenodo> result = context()
                .selectFrom(DUMP_RUN_ZENODO)
                .where(conditions)
                .orderBy(DUMP.ID.asc())
                .limit(limit)
                .fetch()
                .map(Database::recordToDumpRunZenodo);
        Collections.reverse(result);

        if (result.size() < limit && extend && before != null) {
            result.addAll(context()
                    .selectFrom(DUMP_RUN_ZENODO)
                    .where(DUMP.ID.le(before))
                    .orderBy(DUMP.ID.desc())
                    .limit(limit - result.size())
                    .fetch()
                    .map(Database::recordToDumpRunZenodo)
            );
        }

        return result;
    }

    /**
     * Like {@link #getRecentDumpsPrev(Integer, Integer, boolean)}, but for dumps created before the given dump.
     */
    public List<DumpRunZenodo> getRecentDumpsNext(@Nullable Integer after, Integer limit) {
        Collection<Condition> conditions = Collections.emptyList();
        if (after != null) {
            conditions = Collections.singleton(DUMP.ID.lt(after));
        }
        return context()
                .selectFrom(DUMP_RUN_ZENODO)
                .where(conditions)
                .orderBy(DUMP.ID.desc())
                .limit(limit)
                .fetch()
                .map(Database::recordToDumpRunZenodo);
    }

    public RunStats getRecentRunStats() {
        final long entityCount = context()
                .select(RUN.COUNT).from(RUN)
                .where(RUN.FINISHED_AT.isNotNull())
                .orderBy(RUN.ID.desc())
                .limit(1)
                .fetchOptionalInto(long.class)
                .orElse(60000000L);
        final Duration avgTime = context()
                .select(DSL.avg(DSL.localDateTimeDiff(RUN.FINISHED_AT, RUN.STARTED_AT).div(RUN.COUNT)))
                .from(RUN)
                .where(RUN.FINISHED_AT.isNotNull())
                .orderBy(RUN.ID)
                .limit(5)
                .fetchOptionalInto(double.class)
                .map(v -> Duration.ofNanos((long) (v * 1000000)))
                .orElse(Duration.ofNanos(300 * 60 * 1000000000L / 60000000));

        return new RunStats(avgTime, entityCount);
    }
}
