package io.github.bennofs.wdumper;

import io.github.bennofs.wdumper.database.Database;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Class that holds all application-level configuration options.
 *
 * The configuration is shared between the runner and the web frontend.
 */
public interface Config extends
        Database.Config,
        DumpRunner.Config,
        Uploader.Config
{
    /**
     * @return the directory where generated dumps are stored
     */
    Path dumpStorageDirectory();

    /**
     * @return the delay to wait between checks for new upload tasks
     */
    Duration uploadInterval();

    /**
     * @return the delay to wait between checks for new dump tasks
     */
    Duration dumpInterval();

    /**
     * @return interval in which run progress is serialized to the database
     */
    Duration runProgressInterval();

    /**
     * In order to combine multiple dump requests into a single run,
     * dump requests need a minimum age before they trigger a new run.
     * As long as the age of some to-be-processed dump is less than this threshold,
     * no new run will be created, except if some dump is older than {@link #maxDumpRequestAge()}.
     *
     * @return minimum age of dumps before they trigger a new run
     */
    Duration minDumpRequestAge();

    /**
     * Dumps which are older than this threshold will always trigger a new run, even if there are
     * some newly created which are more recent than the {@link #minDumpRequestAge} threshold.
     * This is to prevent dumps being delayed for infinite amount of time if new dump requests
     * are created continuously.
     *
     * @see #minDumpRequestAge()
     * @return maximum age of dumps in the queue of to-be-procssed dumps
     */
    Duration maxDumpRequestAge();

    /**
     * @return maximum size of the preview generated for zenodo in bytes
     */
    int previewSize();

    /**
     * @return JDBC URL specifying the MySQL database to connect to.
     */
    URI databaseAddress();

    /**
     * @return maximum lifetime of a single database connection.
     */
    Duration databaseMaxLifetime();

    /**
     * @return API token for the main zenodo instance. Empty if not configured.
     */
    Optional<String> zenodoReleaseToken();

    /**
     * @return API token for the sandbox zenodo instance. Empty if not configured.
     */
    Optional<String> zenodoSandboxToken();
}
