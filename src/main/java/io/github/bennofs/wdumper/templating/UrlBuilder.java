package io.github.bennofs.wdumper.templating;

import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlBuilder {
    private final URI publicAddress;

    public UrlBuilder(URI publicAddress) {
        this.publicAddress = publicAddress;
    }

    public String urlPathString(String relative) {
        return urlPath(relative).toString();
    }

    public URI urlPath(String relative) {
        try {
            return new URIBuilder(publicAddress.resolve(relative)).setScheme(null).build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid uri", e);
        }
    }

    public String urlPrefixString(String relative) {
        return urlPathString(relative).replaceAll("/+$", "") + "/";
    }
}
