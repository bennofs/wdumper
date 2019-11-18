package io.github.bennofs.wdumper;

public class Constants {
    // time to wait between checks for new upload tasks
    public static final int UPLOAD_INTERVAL_MILLIS = 10000;
    // time to wait between checks for new dump tasks
    public static final int DUMP_INTERVAL_MILLIS = 10 * 60 * 1000;
    // only process dump tasks at least this old
    public static final int RECENT_MIN_MINUTES = 20;
    // only process dump tasks at least this new
    public static final int RECENT_MAX_MINUTES = 60;
}
