package io.github.bennofs.wdumper.zenodo;

import io.github.bennofs.wdumper.jooq.enums.ZenodoTarget;
import org.apache.http.impl.client.CloseableHttpClient;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Objects;

public class ZenodoApiProviderImpl implements ZenodoApiProvider {
    private final Provider<CloseableHttpClient> httpClientProvider;
    private final ZenodoConfiguration zenodoConfiguration;

    @Inject public ZenodoApiProviderImpl(Provider<CloseableHttpClient> httpClientProvider, ZenodoConfiguration zenodoConfiguration) {
        this.httpClientProvider = httpClientProvider;
        this.zenodoConfiguration = zenodoConfiguration;
    }

    @Override
    public @Nullable ZenodoApi getZenodoApiFor(ZenodoTarget target) {
        Objects.requireNonNull(target);
        switch(target) {
            case SANDBOX: {
                return zenodoConfiguration.sandboxToken()
                        .map(token -> new ZenodoApi(httpClientProvider.get(), ZenodoApi.SANDBOX_URI, token))
                        .orElse(null);
            }
            case RELEASE: {
                return zenodoConfiguration.releaseToken()
                        .map(token -> new ZenodoApi(httpClientProvider.get(), ZenodoApi.MAIN_URI, token))
                        .orElse(null);
            }
            default: throw new RuntimeException("non-exhaustive");
        }
    }
}
