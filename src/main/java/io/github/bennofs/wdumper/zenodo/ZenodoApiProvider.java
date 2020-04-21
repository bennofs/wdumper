package io.github.bennofs.wdumper.zenodo;

import io.github.bennofs.wdumper.jooq.enums.ZenodoTarget;
import io.github.bennofs.wdumper.zenodo.ZenodoApi;

import javax.annotation.Nullable;

/**
 * An interface to retrieve the Zenodo API instance for a given target (main zenodo or sandbox).
 */
public interface ZenodoApiProvider {
    /**
     * Get the ZenodoApi instance for the given target
     *
     * @param target API target (main or sandbox)
     * @return ZenodoApi instance, or null if no instance is available for the given target
     *         (for example, this can happen if no API key was configured for that target)
     */
    @Nullable
    ZenodoApi getZenodoApiFor(ZenodoTarget target);
}
