package io.github.bennofs.wdumper.database;

import com.google.common.base.MoreObjects;

public class ZenodoTask {
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
