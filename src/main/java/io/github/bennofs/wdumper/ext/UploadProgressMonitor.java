package io.github.bennofs.wdumper.ext;

@FunctionalInterface
public interface UploadProgressMonitor {
    /**
     * Called by the uploader after writing data.
     *
     * @param uploaded Number of bytes already uploaded
     * @param total Total number of bytes of this upload (includes both already uploaded and still to be uploaded bytes)
     */
    void accept(long uploaded, long total);
}
