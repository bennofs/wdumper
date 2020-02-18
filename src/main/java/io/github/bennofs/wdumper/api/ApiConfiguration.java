package io.github.bennofs.wdumper.api;

import java.net.URI;

public class ApiConfiguration {
    /**
     * Root URL of the API used by the frontend.
     */
    public String apiRoot;

    /**
     * Address of the frontend user interface.
     */
    public URI publicAddress;

    public void apiRoot(String root) {
        this.apiRoot = root;
    }

    public void publicAddress(URI publicAddress) {
        this.publicAddress = publicAddress;
    }
}
