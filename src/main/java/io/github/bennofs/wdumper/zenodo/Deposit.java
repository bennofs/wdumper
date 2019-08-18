package io.github.bennofs.wdumper.zenodo;

import com.fasterxml.jackson.annotation.*;
import com.google.common.base.MoreObjects;
import io.github.bennofs.wdumper.ext.ProgressHttpEntityWrapper;
import kong.unirest.HttpResponse;
import kong.unirest.ProgressMonitor;
import kong.unirest.UnirestInstance;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


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
    public static class FileLinks {
        public final String download;
        public final String self;

        @JsonCreator
        public FileLinks(
                @JsonProperty("download") String download,
                @JsonProperty("self") String self
        ) {
            this.download = download;
            this.self = self;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DepositFile {
        public final String filename;
        public final String checksum;
        public final String id;
        public final FileLinks links;

        public DepositFile(
                @JsonProperty("filename") String filename,
                @JsonProperty("checksum") String checksum,
                @JsonProperty("id") String id,
                @JsonProperty("links") FileLinks links
        ) {
            this.filename = filename;
            this.checksum = checksum;
            this.id = id;
            this.links = links;
        }

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
        final HttpResponse<String> response = this.unirest.post(this.links.discard)
                .header("Content-Type", "application/json")
                .asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }

    public void delete() throws ZenodoException {
        final HttpResponse<String> response = this.unirest.delete(this.links.self)
                .header("Content-Type", "application/json")
                .asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }


    public void publish() throws ZenodoException {
        final HttpResponse<String> response = this.unirest.post(this.links.publish).asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }

    public void addFile(String filename, String value, ProgressMonitor monitor) throws ZenodoException {
        final HttpResponse<String> response = this.unirest.post(this.links.files)
                .multiPartContent()
                .field("file", value.getBytes(StandardCharsets.UTF_8), filename)
                .uploadMonitor(monitor)
                .asString();
        if (!response.isSuccess())
            Zenodo.handleError(response);
    }

    public void addFile(String filename, File value, ProgressMonitor monitor) throws ZenodoException {
        final HttpClient client = HttpClientBuilder.create()
                .setDefaultHeaders(this.unirest.config().getDefaultHeaders().all().stream()
                        .map(header -> new BasicHeader(header.getName(), header.getValue()))
                        .collect(Collectors.toList())
                )
                .build();
        final HttpPut request = new HttpPut(this.links.bucket + "/" + value.getName());
        request.setHeader("Content-Type", "application/octet-stream");
        request.setEntity(new ProgressHttpEntityWrapper(new FileEntity(value), monitor));

        try {
            final org.apache.http.HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() / 100 != 2) {
                String message = "http request for upload failed: status code " + response.getStatusLine().getStatusCode();
                message += ", message: " + IOUtils.toString(response.getEntity().getContent());
                throw new ZenodoException(message);
            }
        } catch(IOException e) {
            throw new ZenodoException("http request for upload failed: " + e.toString());
        }
    }


    public void putMetadata(String title, String description, List<Creator> creators) throws ZenodoException {
        final HttpResponse<String> response = this.unirest.put(this.links.self)
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

    public DepositFile[] getFiles() throws ZenodoException {
        final HttpResponse<DepositFile[]> response = this.unirest.get(this.links.files)
                .asObject(DepositFile[].class);
        if (!response.isSuccess())
            Zenodo.handleError(response);

        return response.getBody();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("doi", doi)
                .add("id", id)
                .toString();
    }
}
