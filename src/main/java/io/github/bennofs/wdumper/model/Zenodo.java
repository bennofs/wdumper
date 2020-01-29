package io.github.bennofs.wdumper.model;

import java.sql.Timestamp;

public class Zenodo {
    public enum Target {
        SANDBOX,
        RELEASE
    }

    public int id;
    public int deposit_id;
    public int dump_id;
    public String doi;
    public Target target;

    public Timestamp created_at;
    public Timestamp started_at;
    public Timestamp completed_at;

    public long uploaded_bytes;
}
