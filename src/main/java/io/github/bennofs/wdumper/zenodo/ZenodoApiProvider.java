package io.github.bennofs.wdumper.zenodo;

import io.github.bennofs.wdumper.jooq.enums.ZenodoTarget;
import io.github.bennofs.wdumper.zenodo.ZenodoApi;

import javax.annotation.Nullable;

public interface ZenodoApiProvider {
    @Nullable
    ZenodoApi getZenodoApiFor(ZenodoTarget target);
}
