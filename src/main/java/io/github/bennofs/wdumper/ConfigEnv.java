package io.github.bennofs.wdumper;

import javax.annotation.Nullable;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

public class ConfigEnv implements Config {
    private final Path dumpStorageDirectory;
    private final Duration uploadInterval;
    private final Duration dumpInterval;
    private final Duration runProgressInterval;
    private final Duration minDumpRequestAge;
    private final Duration maxDumpRequestAge;
    private final int previewSize;
    private final URI databaseAddress;
    private final @Nullable String zenodoReleaseToken;
    private final @Nullable String zenodoSandboxToken;

    private static int intFromEnv(String env, int def) {
        try {
            return Integer.parseInt(Objects.requireNonNullElse(System.getenv(env), "" + def));
        } catch(NumberFormatException e) {
            throw new RuntimeException("cannot parse for config option " + env + " from environment as integer");
        }
    }

    public ConfigEnv() {
        this.dumpStorageDirectory = Path.of(Objects.requireNonNullElse(System.getenv("DUMPS_PATH"), "frontend/dumpfiles/generated"));
        this.uploadInterval = Duration.of(intFromEnv("UPLOAD_INTERVAL_MINUTES", 1), ChronoUnit.MINUTES);
        this.dumpInterval = Duration.of(intFromEnv("DUMP_INTERVAL_MINUTES", 10), ChronoUnit.MINUTES);
        this.runProgressInterval = Duration.of(intFromEnv("PROGRESS_INTERVAL", 60), ChronoUnit.SECONDS);
        this.minDumpRequestAge = Duration.of(intFromEnv("RECENT_MIN_MINUTES", 20), ChronoUnit.MINUTES);
        this.maxDumpRequestAge = Duration.of(intFromEnv("RECENT_MAX_MINUTES", 60), ChronoUnit.MINUTES);
        this.previewSize = 0x1000000;

        final String addressFromEnv = System.getenv("DB_ADDRESS");
        if (addressFromEnv == null) {
            final String dbHost = Objects.requireNonNullElse(System.getenv("DB_HOST"), "localhost");
            final String dbName = Objects.requireNonNullElse(System.getenv("DB_NAME"), "wdumper");
            final String dbUser = Objects.requireNonNullElse(System.getenv("DB_USER"), "root");
            final String dbPassword = Objects.requireNonNullElse(System.getenv("DB_PASSWORD"), "");
            this.databaseAddress = URI.create("jdbc:mysql://" + dbHost + "/" + dbName + "?sslMode=DISABLED&user=" + dbUser + "&password=" + dbPassword);
        } else {
            this.databaseAddress = URI.create(addressFromEnv);
        }

        this.zenodoReleaseToken = System.getenv("ZENODO_TOKEN");
        this.zenodoSandboxToken = System.getenv("ZENODO_SANDBOX_TOKEN");
    }

    @Override
    public Path dumpStorageDirectory() {
        return dumpStorageDirectory;
    }

    @Override
    public Duration uploadInterval() {
        return uploadInterval;
    }

    @Override
    public Duration dumpInterval() {
        return dumpInterval;
    }

    @Override
    public Duration runProgressInterval() {
        return runProgressInterval;
    }

    @Override
    public Duration minDumpRequestAge() {
        return minDumpRequestAge;
    }

    @Override
    public Duration maxDumpRequestAge() {
        return maxDumpRequestAge;
    }

    @Override
    public int previewSize() {
        return previewSize;
    }

    @Override
    public URI databaseAddress() {
        return databaseAddress;
    }

    @Override
    public Optional<String> zenodoReleaseToken() {
        return Optional.ofNullable(zenodoReleaseToken);
    }

    @Override
    public Optional<String> zenodoSandboxToken() {
        return Optional.ofNullable(zenodoSandboxToken);
    }
}
