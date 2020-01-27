package io.github.bennofs.wdumper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bennofs.wdumper.database.Database;
import io.github.bennofs.wdumper.database.DumpInfo;
import io.github.bennofs.wdumper.database.ZenodoTask;
import io.github.bennofs.wdumper.interfaces.DumpStatusHandler;
import io.github.bennofs.wdumper.zenodo.Deposit;
import io.github.bennofs.wdumper.zenodo.Zenodo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * This class handles the upload of finished dumps to zenodo.
 */
public class Uploader implements Runnable {
    private final Database db;
    private final Zenodo zenodo;
    private final Zenodo zenodoSandbox;
    private final Path outputDirectory;
    private final Object runCompletedEvent;
    private final ObjectMapper mapper;

    public Uploader(Database db, Zenodo zenodo, Zenodo zenodoSandbox, Path outputDirectory, Object runCompletedEvent) {
        this.mapper = new ObjectMapper();
        this.db = db;
        this.zenodo = zenodo;
        this.zenodoSandbox = zenodoSandbox;
        this.outputDirectory = outputDirectory;
        this.runCompletedEvent = runCompletedEvent;
    }

    static String generatePreview(Path dumpPath) throws IOException {
        try (final InputStream in = new GZIPInputStream(Files.newInputStream(dumpPath))) {
            final byte[] buffer = new byte[Constants.PREVIEW_SIZE];
            int end = 0;
            while (end != buffer.length) {
                int r = in.read(buffer, end, buffer.length - end);
                if (r <= 0) break;
                end += r;
            }
            // search backward for last newline
            while (end > 0 && buffer[end-1] != 0xa) {
                end--;
            }
            return new String(buffer, 0, end, StandardCharsets.UTF_8);
        }
    }

    private void processUpload() throws InterruptedException {
        final List<ZenodoTask> tasks;

        // check if there a tasks to upload
        synchronized (this.runCompletedEvent) {
            tasks = db.getZenodoTasks(1);

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
                final Deposit.DepositFile[] files = deposit.getFiles();

                if (Arrays.stream(files).noneMatch(file -> file.filename.equals("wdumper-spec.json"))) {
                    final String dumpSpec = db.getDumpSpec(task.dump_id);
                    deposit.addFile("wdumper-spec.json", dumpSpec, (field, fileName, bytesWritten, totalBytes) -> {
                    });
                }

                // upload short preview in plain text, uncompressed
                if (Arrays.stream(files).noneMatch(file -> file.filename.equals("preview.nt"))) {
                    final String preview = generatePreview(outputPath);
                    deposit.addFile("preview.nt", preview, (field, fileName, bytesWritten, totalBytes) -> {
                    });
                }

                if (Arrays.stream(files).noneMatch(file -> file.filename.equals("info.json"))) {
                    final DumpInfo info = db.getDumpInfo(task.dump_id);
                    deposit.addFile("info.json", mapper.writeValueAsString(info), (field, fileName, bytesWritten, totalBytes) -> {
                    });
                }

                try (final UploadProgressMonitor progress = new UploadProgressMonitor(db, task.id)) {
                    deposit.addFile(outputPath.getFileName().toString(), outputPath.toFile(), progress);
                }
                deposit.publish();

                System.err.println("finished upload: " + task.toString());
                db.setUploadFinished(task.id);
            } catch(Exception e) {
                System.err.println("upload failed");
                e.printStackTrace();
                db.logUploadMessage(task.dump_id, task.id, DumpStatusHandler.ErrorLevel.CRITICAL, e.toString());
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
