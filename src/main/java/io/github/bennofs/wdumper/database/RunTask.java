package io.github.bennofs.wdumper.database;

import java.util.List;

public class RunTask {
    public int runId;
    public List<DumpTask> dumps;

    public RunTask(int runId, List<DumpTask> dumps) {
        this.runId = runId;
        this.dumps = dumps;
    }
}
