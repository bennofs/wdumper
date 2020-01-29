package io.github.bennofs.wdumper.model;

import java.sql.Timestamp;

public class Run {
    public int id;
    public Timestamp started_at;
    public Timestamp finished_at;
    public int count;
    public String tool_version;
    public String wdtk_version;
    public String dump_date;
}
