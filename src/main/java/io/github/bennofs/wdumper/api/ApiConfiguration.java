package io.github.bennofs.wdumper.api;

import com.google.auto.value.AutoValue;

import java.net.URI;

@AutoValue
public abstract class ApiConfiguration {
    /**
     * Root URL of the API used by the frontend.
     */
    public abstract String apiRoot();

    /**
     * Address of the frontend user interface.
     */
    public abstract URI publicAddress();

    public static Builder builder() {
        return new AutoValue_ApiConfiguration.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder apiRoot(String apiRoot);
        public abstract Builder publicAddress(URI publicAddress);

        public abstract ApiConfiguration build();
    }
}
