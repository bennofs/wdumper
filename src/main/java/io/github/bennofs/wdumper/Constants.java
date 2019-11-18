package io.github.bennofs.wdumper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Constants {
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

    private static String readMetaFile(String name, String def) {
        try {
            return new String(Constants.class.getResource("/meta/" + name).openStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch(IOException e) {
            return def;
        }
    }
    public static final String WDTK_VERSION = readMetaFile("wdtk-version", null).trim();
    public static final String TOOL_VERSION = readMetaFile("tool-version", null).trim();
}
