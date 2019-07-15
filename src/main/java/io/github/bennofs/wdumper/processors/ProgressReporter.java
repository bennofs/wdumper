package io.github.bennofs.wdumper.processors;

import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.interfaces.RunnerStatusHandler;
import org.wikidata.wdtk.datamodel.interfaces.*;

public class ProgressReporter implements EntityDocumentDumpProcessor {
    private int count = 0;
    private final int delay;
    private final RunnerStatusHandler runnerStatusHandler;
    private Thread thread;

    public ProgressReporter(int delay, RunnerStatusHandler runnerStatusHandler) {
        this.delay = delay;
        this.runnerStatusHandler = runnerStatusHandler;
    }

    @Override
    public void processItemDocument(ItemDocument itemDocument) {
        this.count += 1;
    }

    @Override
    public void processPropertyDocument(PropertyDocument propertyDocument) {
        this.count += 1;
    }

    @Override
    public void processLexemeDocument(LexemeDocument lexemeDocument) {
        this.count += 1;
    }

    @Override
    public void open() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(ProgressReporter.this.delay * 1000);
                    } catch(InterruptedException e) {
                        break;
                    }

                    runnerStatusHandler.reportProgress(count);
                }
            }
        });
        thread.start();
    }

    @Override
    public void close() {
        // report final count
        runnerStatusHandler.reportProgress(count);

        if (thread == null) return;

        thread.interrupt();
        try {
            thread.join();
        } catch(InterruptedException ignored) {
        }
    }

    private void updateProgress() {

    }
}
