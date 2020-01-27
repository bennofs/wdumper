package io.github.bennofs.wdumper.database;

import com.google.common.base.MoreObjects;

public class ZenodoTask {
    public int id;
    public int deposit_id;
    public int dump_id;
    public String target;

    public ZenodoTask(int id, int deposit_id, int dump_id, String target) {
        this.id = id;
        this.deposit_id = deposit_id;
        this.dump_id = dump_id;
        this.target = target;
    }

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
