package io.github.bennofs.wdumper.web;

import org.apache.http.client.utils.URIBuilder;
import ratpack.func.Action;
import ratpack.handling.Chain;
import ratpack.server.PublicAddress;

import javax.inject.Inject;
import java.net.URISyntaxException;

public class UrlBuilder {
    private final PublicAddress publicAddress;

    @Inject
    public UrlBuilder(PublicAddress publicAddress) {
        this.publicAddress = publicAddress;
    }

    public String urlPath(String relative) {
        try {
            return new URIBuilder(publicAddress.builder().path(relative).build()).setScheme(null).build().toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid uri", e);
        }
    }

    public void api(Chain root, Action<? super Chain> action) throws Exception {
        root.prefix("api", action);
    }

    public String urlPrefix(String relative) {
        return urlPath(relative).replaceAll("/+$", "") + "/";
    }
}
