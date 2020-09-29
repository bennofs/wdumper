package io.github.bennofs.wdumper;

import com.google.auto.value.AutoValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Information about the build we are running.
 */
@AutoValue
public abstract class BuildConfig {

    /**
     * @return the version of the wikidata toolkit that this tool was built against
     */
    public abstract String wdtkVersion();

    /**
     * @return the version of the application
     */
    public abstract String toolVersion();

    /**
     * @return the build config of this build
     */
    public static BuildConfig retrieve() {
        return BuildConfig.builder()
                .toolVersion(readMetaFile("tool-version"))
                .wdtkVersion(readMetaFile("wdtk-version"))
                .build();
    }

    private static String readMetaFile(String name) {
        try {
            return new String(BuildConfig.class.getResource("/meta/" + name).openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("initialization error: version information could not be read", e);
        }
    }

    private static Builder builder() {
        return new AutoValue_BuildConfig.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {
        public abstract Builder wdtkVersion(String wdtkVersion);

        public abstract Builder toolVersion(String toolVersion);

        public abstract BuildConfig build();
    }
}
