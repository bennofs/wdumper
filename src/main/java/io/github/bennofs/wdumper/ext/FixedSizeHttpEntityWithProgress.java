package io.github.bennofs.wdumper.ext;

import org.apache.commons.io.output.ProxyOutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

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
    private final UploadProgressMonitor monitor;

    protected static final int OUTPUT_BUFFER_SIZE = 1<<16;

    public FixedSizeHttpEntityWithProgress(InputStream source, long size, UploadProgressMonitor monitor) {
        this.source = source;
        this.size = size;
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
    public InputStream getContent() throws IOException {
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
            while (true) {
                int readCount = source.read(buffer);
                if (readCount == -1) break;

                outStream.write(buffer, 0, readCount);
                this.written += readCount;
                this.monitor.accept(written, size);
            }
        } finally {
            source.close();
        }

        if (this.written != size) {
            throw new IllegalStateException(String.format(
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
    public void consumeContent() throws IOException {
    }
}
