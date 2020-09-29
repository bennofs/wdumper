package io.github.bennofs.wdumper.zenodo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;


@JsonIgnoreProperties(ignoreUnknown = true)
public class Deposit {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FileLinks {
        public String download;
        public String self;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("download", download)
                    .add("self", self)
                    .toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DepositFile {
        public String filename;
        public String checksum;
        public String id;

        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        FileLinks links;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("filename", filename)
                    .add("checksum", checksum)
                    .add("id", id)
                    .toString();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Links {
        public String discard;
        public String edit;
        public String files;
        public String publish;
        public String newversion;
        public String bucket;
        public String self;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("discard", discard)
                    .add("edit", edit)
                    .add("files", files)
                    .add("publish", publish)
                    .add("newversion", newversion)
                    .add("bucket", bucket)
                    .add("self", self)
                    .toString();
        }
    }

    public Metadata metadata;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    public int id;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    Links links;
}
