package io.github.bennofs.wdumper.model;

import java.sql.Timestamp;

public class Dump {
    public int id;
    public String title;
    public String description;
    public String spec;
    public Timestamp created_at;
    public Integer run_id;
    public Long compressed_size;
    public Long entity_count;
    public Long statement_count;
    public Long triple_count;
}
