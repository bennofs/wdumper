package io.github.bennofs.wdumper.zenodo;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;
import java.util.Optional;

@AutoValue
public abstract class ZenodoConfiguration {
    public abstract Optional<String> sandboxToken();
    public abstract Optional<String> releaseToken();

    public static Builder builder() {
        return new AutoValue_ZenodoConfiguration.Builder();
    }


    @AutoValue.Builder()
    public abstract static class Builder {
        public abstract Builder sandboxToken(@Nullable String newSandboxToken);
        public abstract Builder releaseToken(@Nullable String newReleaseToken);
        public abstract ZenodoConfiguration build();
    }
}
