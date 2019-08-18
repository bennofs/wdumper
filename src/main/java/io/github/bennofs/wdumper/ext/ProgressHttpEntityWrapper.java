package io.github.bennofs.wdumper.ext;

import kong.unirest.ProgressMonitor;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.ProxyOutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.entity.HttpEntityWrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProgressHttpEntityWrapper extends HttpEntityWrapper {
    private long bytes = 0;
    private final ProgressMonitor monitor;

    public ProgressHttpEntityWrapper(HttpEntity wrappedEntity, ProgressMonitor monitor) {
        super(wrappedEntity);
        this.monitor = monitor;
    }

    @Override
    public InputStream getContent() throws IOException {
        throw new UnsupportedOperationException("impossible");
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
        super.writeTo(new ProxyOutputStream(outStream) {
            @Override
            protected void afterWrite(int n) throws IOException {
                bytes += n;
                monitor.accept("file", "", bytes, wrappedEntity.getContentLength());
            }
        });
    }
}
