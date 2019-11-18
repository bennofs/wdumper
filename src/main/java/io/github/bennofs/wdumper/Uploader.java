package io.github.bennofs.wdumper;

import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.database.ZenodoTask;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.zenodo.Deposit;
import io.github.bennofs.wdumper.zenodo.Zenodo;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * This class handles the upload of finished dumps to zenodo.
 */
public class Uploader implements Runnable {
    private final Database db;
    private final Zenodo zenodo;
    private final Zenodo zenodoSandbox;
    private final Path outputDirectory;
    private final Object runCompletedEvent;

    public Uploader(Database db, Zenodo zenodo, Zenodo zenodoSandbox, Path outputDirectory, Object runCompletedEvent) {
        this.db = db;
        this.zenodo = zenodo;
        this.zenodoSandbox = zenodoSandbox;
        this.outputDirectory = outputDirectory;
        this.runCompletedEvent = runCompletedEvent;
    }

    private void processUpload() throws InterruptedException {
        final List<ZenodoTask> tasks;

        // check if there a tasks to upload
        synchronized (this.runCompletedEvent) {
            tasks = this.db.withHandle(handle -> db.getZenodoTasks(handle, 1));

            // if there are no tasks, wait for either a run to complete or the check interval timeout to expire
            if (tasks.isEmpty()) {
                this.runCompletedEvent.wait(Constants.UPLOAD_INTERVAL_MILLIS);
                return;
            }
        }

        // for each task, do the upload
        for (ZenodoTask task : tasks) {
            try {
                System.err.println("starting upload: " + task.toString());

                final Zenodo api = task.target.equals("RELEASE") ? this.zenodo : this.zenodoSandbox;
                final Path outputPath = DumpRunner.getOutputPath(outputDirectory, task.dump_id);

                final Deposit deposit = api.getDeposit(task.deposit_id);

                if (Arrays.stream(deposit.getFiles()).noneMatch(file -> file.filename.equals("wdumper-spec.json"))) {
                    final String dumpSpec = db.withHandle(handle -> db.getDumpSpec(handle, task.dump_id));
                    deposit.addFile("wdumper-spec.json", dumpSpec, (field, fileName, bytesWritten, totalBytes) -> {
                    });
                }

                try (final UploadProgressMonitor progress = new UploadProgressMonitor(db, task.id)) {
                    deposit.addFile(outputPath.getFileName().toString(), outputPath.toFile(), progress);
                }
                deposit.publish();

                System.err.println("finished upload: " + task.toString());
                db.useHandle(handle -> db.setUploadFinished(handle, task.id));
            } catch(Exception e) {
                System.err.println("upload failed");
                e.printStackTrace();
                db.useHandle(handle -> {
                    db.logUploadMessage(handle, task.dump_id, task.id, DumpStatusHandler.ErrorLevel.CRITICAL, e.toString());
                });
            }
        }
    }

    @Override
    public void run() {
        // process upload tasks until interrupted
        while (true) {
            try {
                processUpload();
            } catch(InterruptedException e) {
                break;
            }
        }
    }
}
