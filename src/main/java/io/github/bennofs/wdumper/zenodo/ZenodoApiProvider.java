package io.github.bennofs.wdumper.zenodo;

import io.github.bennofs.wdumper.jooq.enums.ZenodoTarget;
import io.github.bennofs.wdumper.zenodo.ZenodoApi;

public interface ZenodoApiProvider {
    ZenodoApi getZenodoApiFor(ZenodoTarget target);
}
