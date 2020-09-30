package io.github.bennofs.wdumper.zenodo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.collect.Lists;
import io.github.bennofs.wdumper.ext.FixedSizeHttpEntityWithProgress;
import io.github.bennofs.wdumper.ext.UploadProgressMonitor;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

public class ZenodoApi {

    private final CloseableHttpClient http;
    private final URI baseUri;
    private final String token;
    private final ObjectReader objectReader;
    private final ObjectWriter objectWriter;

    static final ObjectMapper MAPPER;
    static {
        MAPPER = new ObjectMapper();
        MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        MAPPER.registerModule(new Jdk8Module());
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.registerModule(new ParameterNamesModule());
    }

    public static final URI SANDBOX_URI = URI.create("https://sandbox.zenodo.org/api/");
    public static final URI MAIN_URI = URI.create("https://zenodo.org/api/");

    public ZenodoApi(CloseableHttpClient http, URI baseUri, String token) {
        Objects.requireNonNull(token);
        Objects.requireNonNull(baseUri);
        Objects.requireNonNull(http);

        this.baseUri = baseUri;
        this.http = http;
        this.token = token;

        this.objectReader = MAPPER.reader();
        this.objectWriter = MAPPER.writer();
    }

    private String endpointUrl(String suburi) {
        return this.baseUri.resolve(suburi).toString();
    }

    /**
     * Configure request defaults, such as adding authorization headers.
     *
     * @param request The request to configure.
     */
    private void configureRequest(HttpRequest request) {
        request.addHeader("Authorization", "Bearer " + token);
    }

    /**
     * Check that the http response is some 2xx-Ok response, throw an exception otherwise.
     * @param r The response to check
     * @throws ZenodoApiException if the request status code is not 2xx
     */
    private void checkResponse(HttpResponse r) throws ZenodoApiException {
        final int statusCode = r.getStatusLine().getStatusCode();
        if (r.getStatusLine().getStatusCode() / 100 == 2) return;

        try {
            final var entity = r.getEntity();
            final String body = IOUtils.toString(entity.getContent(), StandardCharsets.UTF_8);
            throw new ZenodoApiException(statusCode, body);
        } catch (IOException e) {
            throw new ZenodoApiException(statusCode, e);
        }
    }

    /**
     * Converts an unexpected JSON exception into a RuntimeException with a high-level error message.
     *
     * During API calls, JSON exceptions should never occur since there is no user-generated JSON involved.
     * If it does fail, there is no way to recover for the user since it then is a bug in the implementation
     * of this class.
     */
    private static RuntimeException jsonException(JsonProcessingException e) {
        return new RuntimeException("communication with Zenodo API failed due to a json processing error", e);
    }

    /**
     * Convers an unexpected HTTP exception into a RuntimeException with a high-level error message.
     *
     * In contrast to IOExceptions, HTTP protocol exceptions can only happen if there
     * are bugs in the implementation, so we convert them into unchecked RuntimeExceptions here.
     */
    private static RuntimeException httpException(ClientProtocolException e) {
        return new RuntimeException("communication with Zenodo API failed due to a http protocol error", e);
    }

    /**
     * Executes a HTTP request and parses the result as JSON.
     *
     * @param request The request to execute
     * @param cls Class of the response type
     * @return The parsed response
     */
    private <Response> Response executeRequest(HttpUriRequest request, Class<Response> cls) throws ZenodoApiException, IOException {
        try {
            try (final CloseableHttpResponse response = http.execute(request))  {
                checkResponse(response);

                if (response.getEntity() == null) return null;
                return objectReader.forType(cls).readValue(response.getEntity().getContent());
            }
        } catch (JsonProcessingException e) {
            throw jsonException(e);
        } catch (ClientProtocolException e) {
            throw httpException(e);
        }
    }

    /**
     * Performs a simple POST request with JSON content and response.
     *
     * @param endpoint The target of the request, relative to the API base (example: {@code "deposit/depositions"})
     * @param r The object to serialize to JSON and send as request body.
     * @param cls Class of the response type.
     * @return The parsed response. Null can be returned if the http client returns null.
     */
    private <Response, Request> Response postJson(String endpoint, Request r, Class<Response> cls) throws IOException, ZenodoApiException {
        HttpPost request = new HttpPost(endpointUrl(endpoint));
        configureRequest(request);
        try {
            request.setEntity(new StringEntity(objectWriter.writeValueAsString(r), APPLICATION_JSON));
        } catch (JsonProcessingException e) {
            throw jsonException(e);
        }

        return executeRequest(request, cls);
    }

    /**
     * Performs a simple PUT request, parsing the response as JSON.
     *
     * @see #postJson(String, Object, Class)
     */
    private <Response, Request> Response putJson(String endpoint, Request r, Class<Response> cls) throws IOException, ZenodoApiException {
        HttpPut request = new HttpPut(endpointUrl(endpoint));
        configureRequest(request);
        try {
            request.setEntity(new StringEntity(objectWriter.writeValueAsString(r), APPLICATION_JSON));
        } catch (JsonProcessingException e) {
            throw jsonException(e);
        }

        return executeRequest(request, cls);
    }

    /**
     * Performs a simple GET request, parsing the response as JSON.
     *
     * @see #postJson(String, Object, Class)
     */
    private <Response> Response getJson(String endpoint, Class<Response> cls) throws IOException, ZenodoApiException {
        HttpGet request = new HttpGet(endpointUrl(endpoint));
        configureRequest(request);
        return executeRequest(request, cls);
    }

    /**
     * Performs a simple DELETE request, ignoring the response.
     */
    private void httpDelete(String endpoint) throws IOException, ZenodoApiException {
        HttpDelete request = new HttpDelete(endpointUrl(endpoint));
        configureRequest(request);
        try {
            try (final CloseableHttpResponse response = http.execute(request)) {
                checkResponse(response);
            }
        } catch (ClientProtocolException e) {
            throw httpException(e);
        }
    }

    /**
     * Performs a simple POST request with empty body, ignoring the response.
     */
    private void httpPost(String endpoint) throws IOException, ZenodoApiException {
        HttpPost request = new HttpPost(endpointUrl(endpoint));
        configureRequest(request);
        try {
            try (final CloseableHttpResponse response = http.execute(request)) {
                checkResponse(response);
            }
        } catch (ClientProtocolException e) {
            throw httpException(e);
        }
    }

    public Deposit createDeposit() throws IOException, ZenodoApiException {
        return postJson("deposit/depositions", Collections.emptyMap(), Deposit.class);
    }

    public Deposit getDeposit(int id) throws IOException, ZenodoApiException {
        return getJson("deposit/depositions/" + id, Deposit.class);
    }

    public Deposit updateDeposit(Deposit deposit) throws IOException, ZenodoApiException {
        return putJson(deposit.links.self, deposit, Deposit.class);
    }

    public void deleteDeposit(int id) throws IOException, ZenodoApiException {
        httpDelete("deposit/depositions/" + id);
    }

    public void publishDeposit(Deposit deposit) throws IOException, ZenodoApiException {
        httpPost(deposit.links.publish);
    }

    public List<Deposit> getRecentDepositions() throws IOException, ZenodoApiException {
        Deposit[] deposits = getJson("deposit/depositions", Deposit[].class);
        return Lists.newArrayList(deposits);
    }

    public Deposit.DepositFile[] getFiles(Deposit deposit) throws IOException, ZenodoApiException {
        return getJson(deposit.links.files, Deposit.DepositFile[].class);
    }

    /**
     * Uploads a file to the given deposit.
     *
     * Like {@link #addFile(Deposit, String, InputStream, long, UploadProgressMonitor)}, but takes a String as input.
     */
    public void addFile(Deposit deposit, String filename, String value, UploadProgressMonitor progress) throws IOException, ZenodoApiException {
        final byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        addFile(deposit, filename, new ByteArrayInputStream(encoded), encoded.length, progress);
    }

    /**
     * Uploads a file to a deposit.
     *
     * @param deposit Deposit to upload to
     * @param filename Name of the file to create in the deposit
     * @param source InputStream from which data for the file is read
     * @param size Size of file. This must match the amount of data available in the supplied input stream.
     * @param progress Handler for progress feedback during upload.
     *
     * @throws IOException if an underlying IO error occurs during communication
     * @throws ZenodoApiException if an error is returned from the Zenodo API
     */
    public void addFile(Deposit deposit, String filename, InputStream source, long size, UploadProgressMonitor progress) throws IOException, ZenodoApiException {
        // Note: we do not use the POST api call here since the Zenodo API returns 413 Entity Too Large if using
        // POST with large files. PUT works though.
        final HttpPut request = new HttpPut(deposit.links.bucket + "/" + filename);
        configureRequest(request);
        request.setHeader("Content-Type", "application/octet-stream");
        request.setEntity(new FixedSizeHttpEntityWithProgress(source, size, progress));

        try {
            try (CloseableHttpResponse response = http.execute(request)) {
                checkResponse(response);
            }
        } catch(ClientProtocolException e) {
            throw httpException(e);
        }
    }
}
