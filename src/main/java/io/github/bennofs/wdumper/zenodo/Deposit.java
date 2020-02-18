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
    public
    int id;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    Links links;

    /*public void discard() throws ZenodoException {
        final HttpResponse<String> response = this.unirest.post(this.links.discard)
                .header("Content-Type", "application/json")
                .asString();
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
    }*/
}
