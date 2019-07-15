package io.github.bennofs.wdumper.interfaces;

public interface RunnerStatusHandler {
    void start();
    void reportProgress(int count);
    void done();
}
