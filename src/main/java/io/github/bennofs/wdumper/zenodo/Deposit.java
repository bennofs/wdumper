package io.github.bennofs.wdumper.zenodo;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.MoreObjects;
import io.github.bennofs.wdumper.processors.ProgressReporter;
import kong.unirest.ProgressMonitor;
import kong.unirest.UnirestInstance;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@JsonIgnoreProperties(ignoreUnknown = true)
public class Deposit {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DOI {
        final String doi;

        @JsonCreator
        public DOI(@JsonProperty("doi") String doi) {
            this.doi = doi;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        final DOI prereserve_doi;

        @JsonCreator
        public Metadata(
                @JsonProperty("prereserve_doi") DOI prereserve_doi
        ) {
            this.prereserve_doi = prereserve_doi;
        }
    }

    public static class Creator {
        public final String name;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public final String affiliation;

        public Creator(String name, String affiliation) {
            Objects.requireNonNull(name);

            this.name = name;
            this.affiliation = affiliation;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Links {
        final String discard;
        final String edit;
        final String files;
        final String publish;
        final String newversion;
        final String bucket;
        final String self;

        @JsonCreator
        public Links(
                @JsonProperty("discard") String discard,
                @JsonProperty("edit") String edit,
                @JsonProperty("files") String files,
                @JsonProperty("publish") String publish,
                @JsonProperty("newversion") String newversion,
                @JsonProperty("bucket") String bucket,
                @JsonProperty("self") String self
        ) {
            this.discard = discard;
            this.edit = edit;
            this.files = files;
            this.publish = publish;
            this.newversion = newversion;
            this.bucket = bucket;
            this.self = self;
        }
    }

    private final UnirestInstance unirest;
    private String doi;
    private final int id;
    private final Links links;

    @JsonCreator
    public Deposit(
            @JacksonInject("unirest") UnirestInstance unirest,
            @JsonProperty("metadata") Metadata metadata,
            @JsonProperty("id") int id,
            @JsonProperty("links") Links links
    ) {
        this.unirest = unirest;
        this.doi = metadata.prereserve_doi.doi;
        this.id = id;
        this.links = links;
    }

    public String getDoi() {
        return doi;
    }

    public int getId() {
        return id;
    }

    public void discard() throws ZenodoException {
        final var response = this.unirest.post(this.links.discard)
                .header("Content-Type", "application/json")
                .asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }

    public void delete() throws ZenodoException {
        final var response = this.unirest.delete(this.links.self)
                .header("Content-Type", "application/json")
                .asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }


    public void publish() throws ZenodoException {
        final var response = this.unirest.post(this.links.publish).asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }

    public void addFile(String filename, String value, ProgressMonitor monitor) throws ZenodoException {
        final var response = this.unirest.post(this.links.files)
                .multiPartContent()
                .field("file", value.getBytes(StandardCharsets.UTF_8), filename)
                .uploadMonitor(monitor)
                .asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }

    public void addFile(String filename, File value, ProgressMonitor monitor) throws ZenodoException {
        final var response = this.unirest.post(this.links.files)
                .field("file", value, filename)
                .uploadMonitor(monitor)
                .asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }


    public void putMetadata(String title, String description, List<Creator> creators) throws ZenodoException {
        final var response = this.unirest.put(this.links.self)
                .header("Content-Type", "application/json")
                .body(Map.of("metadata", Map.of(
                        "title", title,
                        "upload_type", "dataset",
                        "access_right", "open",
                        "license", "cc-zero",
                        "description", description,
                        "creators", creators,
                        "prereserve_doi", true,
                        "doi", getDoi()
                )))
                .asString();

        if (!response.isSuccess())
            Zenodo.handleError(response);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("doi", doi)
                .add("id", id)
                .toString();
    }
}
