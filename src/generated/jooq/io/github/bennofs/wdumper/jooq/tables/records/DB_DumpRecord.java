/*
 * This file is generated by jOOQ.
 */
package io.github.bennofs.wdumper.jooq.tables.records;


import io.github.bennofs.wdumper.jooq.tables.DB_Dump;

import java.time.LocalDateTime;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record10;
import org.jooq.Row10;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DB_DumpRecord extends UpdatableRecordImpl<DB_DumpRecord> implements Record10<Integer, String, String, LocalDateTime, Integer, Long, Long, Long, Long, String> {

    private static final long serialVersionUID = -1466491676;

    /**
     * Setter for <code>dump.id</code>.
     */
    public void setId(Integer value) {
        set(0, value);
    }

    /**
     * Getter for <code>dump.id</code>.
     */
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>dump.title</code>.
     */
    public void setTitle(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>dump.title</code>.
     */
    public String getTitle() {
        return (String) get(1);
    }

    /**
     * Setter for <code>dump.spec</code>.
     */
    public void setSpec(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>dump.spec</code>.
     */
    public String getSpec() {
        return (String) get(2);
    }

    /**
     * Setter for <code>dump.created_at</code>.
     */
    public void setCreatedAt(LocalDateTime value) {
        set(3, value);
    }

    /**
     * Getter for <code>dump.created_at</code>.
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(3);
    }

    /**
     * Setter for <code>dump.run_id</code>.
     */
    public void setRunId(Integer value) {
        set(4, value);
    }

    /**
     * Getter for <code>dump.run_id</code>.
     */
    public Integer getRunId() {
        return (Integer) get(4);
    }

    /**
     * Setter for <code>dump.compressed_size</code>.
     */
    public void setCompressedSize(Long value) {
        set(5, value);
    }

    /**
     * Getter for <code>dump.compressed_size</code>.
     */
    public Long getCompressedSize() {
        return (Long) get(5);
    }

    /**
     * Setter for <code>dump.entity_count</code>.
     */
    public void setEntityCount(Long value) {
        set(6, value);
    }

    /**
     * Getter for <code>dump.entity_count</code>.
     */
    public Long getEntityCount() {
        return (Long) get(6);
    }

    /**
     * Setter for <code>dump.statement_count</code>.
     */
    public void setStatementCount(Long value) {
        set(7, value);
    }

    /**
     * Getter for <code>dump.statement_count</code>.
     */
    public Long getStatementCount() {
        return (Long) get(7);
    }

    /**
     * Setter for <code>dump.triple_count</code>.
     */
    public void setTripleCount(Long value) {
        set(8, value);
    }

    /**
     * Getter for <code>dump.triple_count</code>.
     */
    public Long getTripleCount() {
        return (Long) get(8);
    }

    /**
     * Setter for <code>dump.description</code>.
     */
    public void setDescription(String value) {
        set(9, value);
    }

    /**
     * Getter for <code>dump.description</code>.
     */
    public String getDescription() {
        return (String) get(9);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record10 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row10<Integer, String, String, LocalDateTime, Integer, Long, Long, Long, Long, String> fieldsRow() {
        return (Row10) super.fieldsRow();
    }

    @Override
    public Row10<Integer, String, String, LocalDateTime, Integer, Long, Long, Long, Long, String> valuesRow() {
        return (Row10) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return DB_Dump.DUMP.ID;
    }

    @Override
    public Field<String> field2() {
        return DB_Dump.DUMP.TITLE;
    }

    @Override
    public Field<String> field3() {
        return DB_Dump.DUMP.SPEC;
    }

    @Override
    public Field<LocalDateTime> field4() {
        return DB_Dump.DUMP.CREATED_AT;
    }

    @Override
    public Field<Integer> field5() {
        return DB_Dump.DUMP.RUN_ID;
    }

    @Override
    public Field<Long> field6() {
        return DB_Dump.DUMP.COMPRESSED_SIZE;
    }

    @Override
    public Field<Long> field7() {
        return DB_Dump.DUMP.ENTITY_COUNT;
    }

    @Override
    public Field<Long> field8() {
        return DB_Dump.DUMP.STATEMENT_COUNT;
    }

    @Override
    public Field<Long> field9() {
        return DB_Dump.DUMP.TRIPLE_COUNT;
    }

    @Override
    public Field<String> field10() {
        return DB_Dump.DUMP.DESCRIPTION;
    }

    @Override
    public Integer component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getTitle();
    }

    @Override
    public String component3() {
        return getSpec();
    }

    @Override
    public LocalDateTime component4() {
        return getCreatedAt();
    }

    @Override
    public Integer component5() {
        return getRunId();
    }

    @Override
    public Long component6() {
        return getCompressedSize();
    }

    @Override
    public Long component7() {
        return getEntityCount();
    }

    @Override
    public Long component8() {
        return getStatementCount();
    }

    @Override
    public Long component9() {
        return getTripleCount();
    }

    @Override
    public String component10() {
        return getDescription();
    }

    @Override
    public Integer value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getTitle();
    }

    @Override
    public String value3() {
        return getSpec();
    }

    @Override
    public LocalDateTime value4() {
        return getCreatedAt();
    }

    @Override
    public Integer value5() {
        return getRunId();
    }

    @Override
    public Long value6() {
        return getCompressedSize();
    }

    @Override
    public Long value7() {
        return getEntityCount();
    }

    @Override
    public Long value8() {
        return getStatementCount();
    }

    @Override
    public Long value9() {
        return getTripleCount();
    }

    @Override
    public String value10() {
        return getDescription();
    }

    @Override
    public DB_DumpRecord value1(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public DB_DumpRecord value2(String value) {
        setTitle(value);
        return this;
    }

    @Override
    public DB_DumpRecord value3(String value) {
        setSpec(value);
        return this;
    }

    @Override
    public DB_DumpRecord value4(LocalDateTime value) {
        setCreatedAt(value);
        return this;
    }

    @Override
    public DB_DumpRecord value5(Integer value) {
        setRunId(value);
        return this;
    }

    @Override
    public DB_DumpRecord value6(Long value) {
        setCompressedSize(value);
        return this;
    }

    @Override
    public DB_DumpRecord value7(Long value) {
        setEntityCount(value);
        return this;
    }

    @Override
    public DB_DumpRecord value8(Long value) {
        setStatementCount(value);
        return this;
    }

    @Override
    public DB_DumpRecord value9(Long value) {
        setTripleCount(value);
        return this;
    }

    @Override
    public DB_DumpRecord value10(String value) {
        setDescription(value);
        return this;
    }

    @Override
    public DB_DumpRecord values(Integer value1, String value2, String value3, LocalDateTime value4, Integer value5, Long value6, Long value7, Long value8, Long value9, String value10) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        value9(value9);
        value10(value10);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached DB_DumpRecord
     */
    public DB_DumpRecord() {
        super(DB_Dump.DUMP);
    }

    /**
     * Create a detached, initialised DB_DumpRecord
     */
    public DB_DumpRecord(Integer id, String title, String spec, LocalDateTime createdAt, Integer runId, Long compressedSize, Long entityCount, Long statementCount, Long tripleCount, String description) {
        super(DB_Dump.DUMP);

        set(0, id);
        set(1, title);
        set(2, spec);
        set(3, createdAt);
        set(4, runId);
        set(5, compressedSize);
        set(6, entityCount);
        set(7, statementCount);
        set(8, tripleCount);
        set(9, description);
    }
}