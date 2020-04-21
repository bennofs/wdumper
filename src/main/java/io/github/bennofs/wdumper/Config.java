package io.github.bennofs.wdumper;

import io.github.bennofs.wdumper.jooq.enums.ZenodoTarget;
import org.apache.commons.lang3.ObjectUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Config {
    private static int intFromEnv(String env, int def) {
        return Integer.parseInt(Objects.requireNonNullElse(System.getenv(env), "" + def));
    }

    // time to wait between checks for new upload tasks
    public static final int UPLOAD_INTERVAL_MILLIS = intFromEnv("UPLOAD_INTERVAL_MINUTES", 1) * 60 * 1000;
    // time to wait between checks for new dump tasks
    public static final int DUMP_INTERVAL_MILLIS = intFromEnv("DUMP_INTERVAL_MINUTES", 10) * 60 * 1000;
    // only process dump tasks at least this old
    public static final int RECENT_MIN_MINUTES = intFromEnv("RECENT_MIN_MINUTES", 20);
    // only process dump tasks at least this new
    public static final int RECENT_MAX_MINUTES = intFromEnv("RECENT_MAX_MINUTES", 60);
    // interval for updating the progress of the current run
    public static final int PROGRESS_INTERVAL = intFromEnv("PROGRESS_INTERVAL", 60);
    // maximum size of the preview in bytes that is generated for zenodo uploads
    public static final int PREVIEW_SIZE = 0x100000;

    private static String readMetaFile(String name) {
        try {
            return new String(Config.class.getResource("/meta/" + name).openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("initialization error: version information could not be read", e);
        }
    }
    public static final String WDTK_VERSION = readMetaFile("wdtk-version").trim();
    public static final String TOOL_VERSION = readMetaFile("tool-version").trim();

    public static String constructDBUri() {
        final String dbHost = ObjectUtils.defaultIfNull(System.getenv("DB_HOST"), "localhost");
        final String dbName = ObjectUtils.defaultIfNull(System.getenv("DB_NAME"), "wdumper");
        final String dbUser = ObjectUtils.defaultIfNull(System.getenv("DB_USER"), "root");
        final String dbPassword = ObjectUtils.defaultIfNull(System.getenv("DB_PASSWORD"), "");

        return "jdbc:mysql://" + dbHost + "/" + dbName + "?sslMode=DISABLED&user=" + dbUser + "&password=" + dbPassword;
    }

    public static String getZenodoToken(ZenodoTarget target) {
        Objects.requireNonNull(target, "zenodo target cannot be null");
        switch (target) {
            case SANDBOX:
                return System.getenv("ZENODO_SANDBOX_TOKEN");
            case RELEASE:
                return System.getenv("ZENODO_TOKEN");
        };
        throw new RuntimeException("switch should be exhaustive");
    }
}
