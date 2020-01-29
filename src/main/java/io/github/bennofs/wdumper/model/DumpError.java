package io.github.bennofs.wdumper.model;

import java.sql.Timestamp;

public class DumpError {
    public enum Level {
        CRITICAL,
        ERROR,
        WARNING,
    }

    public int id;
    public Timestamp logged_at;
    public Integer dump_id;
    public Integer run_id;
    public Integer zenodo_id;
    public Level level;
    public String message;
}
