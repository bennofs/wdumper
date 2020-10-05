package io.github.bennofs.wdumper.ext;

import org.apache.http.Header;
import org.apache.http.HttpEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * A HTTP entity for the apache HTTP client that tracks the number of uploaded bytes.
 *
 * This class implements a HttpEntity for input streams where the size is known in advance,
 * allowing it to provide progress feedback and also not requiring chunked transfer encoding.
 */
public class FixedSizeHttpEntityWithProgress implements HttpEntity {
    private long written = 0;
    private final long size;
    private final InputStream source;
    private final SizeMode sizeMode;
    private final UploadProgressMonitor monitor;

    public enum SizeMode {
        EXACT,
        TRUNCATE
    }

    protected static final int OUTPUT_BUFFER_SIZE = 1<<16;

    public FixedSizeHttpEntityWithProgress(InputStream source, long size, SizeMode sizeMode, UploadProgressMonitor monitor) {
        this.source = source;
        this.size = size;
        this.sizeMode = sizeMode;
        this.monitor = monitor;
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public long getContentLength() {
        return this.size;
    }

    @Override
    public Header getContentType() {
        return null;
    }

    @Override
    public Header getContentEncoding() {
        return null;
    }

    @Override
    public InputStream getContent() {
        // we don't support getContent, since we want to track how many bytes have been written
        // this method is not needed, internally writeTo is used
        // see https://stackoverflow.com/a/28276489/2494803
        throw new UnsupportedOperationException("impossible");
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
        Objects.requireNonNull(outStream);

        try {
            final byte[] buffer = new byte[OUTPUT_BUFFER_SIZE];
            while (this.written <= size || sizeMode == SizeMode.EXACT) {
                int readCount = source.read(buffer);
                if (readCount == -1) break;

                if (sizeMode == SizeMode.TRUNCATE && this.written + readCount > this.size) {
                    readCount = (int)(this.size - this.written);
                }

                outStream.write(buffer, 0, readCount);
                this.written += readCount;
                this.monitor.accept(written, size);
            }
        } finally {
            source.close();
        }

        if (this.written != size) {
            throw new IOException(String.format(
                    "number of written bytes (%d) does not equal input source size (%d)",
                    this.written,
                    this.size
            ));
        }
    }

    @Override
    public boolean isStreaming() {
        return true;
    }

    @Override
    @Deprecated
    public void consumeContent() {
    }
}
