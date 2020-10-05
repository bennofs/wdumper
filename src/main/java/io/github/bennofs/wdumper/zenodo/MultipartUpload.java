package io.github.bennofs.wdumper.zenodo;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Stores the state of an in-progress upload which has multiple parts.
 */
public class MultipartUpload {
    public static class Links {
        public URI object;
        public URI bucket;
        public URI self;
    }

    public static class Part {
        public Instant created;
        public Instant updated;
        public String checksum;
        public long partNumber;
        public long startByte;
        public long endByte;
    }

    public String id;
    public Instant created;
    public Instant updated;
    public boolean completed;
    public String key;
    public String bucket;
    public long size;
    public long partSize;
    public long lastPartNumber;
    public long lastPartSize;
    public List<Part> parts;

    public Links links;
}
