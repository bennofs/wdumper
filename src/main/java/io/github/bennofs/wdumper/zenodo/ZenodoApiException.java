package io.github.bennofs.wdumper.zenodo;

import org.apache.commons.lang3.Validate;

/**
 * The ZenodoApiException represents errors returned by the Zenodo API.
 *
 * @see <a href="https://developers.zenodo.org/#errors">Zenodo API documentation: errors</a>
 */
public class ZenodoApiException extends Exception {
    private String responseBody;
    private int responseCode;

    /**
     * Constructs a new ZenodoApiException.
     *
     * @param code HTTP code of the error response
     * @param body Body of the error response
     */
    public ZenodoApiException(int code, String body) {
        super("zenodo API error with http code " + code + " (details: " + body + ")");
        Validate.notNull(body, "argument body must be non-null");

        this.responseCode = code;
        this.responseBody = body;
    }

    /**
     * Constructs a new API exception without a body. This constructor is necessary since reading the
     * response body may fail.
     *
     * @param code The error code of the HTTP response
     * @param cause Exception which occurred during reading of the response body.
     */
    public ZenodoApiException(int code, Throwable cause) {
        super("unexpected http status code " + code, cause);
        this.responseCode = code;
        this.responseBody = null;
    }

    /**
     * @return The body of the error response
     */
    public String getResponseBody() {
        return responseBody;
    }

    /**
     * @return The HTTP status code of the error response
     */
    public int getResponseCode() {
        return responseCode;
    }
}
