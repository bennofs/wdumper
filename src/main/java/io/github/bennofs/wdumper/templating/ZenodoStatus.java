package io.github.bennofs.wdumper.templating;

import io.github.bennofs.wdumper.model.*;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class ZenodoStatus implements ModelExtension {
    private final @Nullable Run run;
    private final Dump dump;
    private final Zenodo zenodo;

    public ZenodoStatus(@Nullable Run run, Dump dump, Zenodo zenodo) {
        this.run = run;
        this.dump = dump;
        this.zenodo = zenodo;
    }

    @Override
    public Object extensionBase() {
        return zenodo;
    }

    public Optional<Progress> uploading() {
        if (waitingForDump().isPresent() || zenodo.completedAt().isPresent() || zenodo.startedAt().isEmpty()) {
            return Optional.empty();
        }
        final Instant startedAt = zenodo.startedAt().get();

        final Duration elapsed = Duration.between(startedAt, Instant.now());
        final Duration remaining = elapsed.multipliedBy(dump.compressedSize()).dividedBy(zenodo.uploadedBytes());
        final long percent = zenodo.uploadedBytes() * 100 / dump.compressedSize();

        final Progress progress = new Progress(
                startedAt,
                remaining,
                percent
        );
        return Optional.of(progress);
    }

    public Optional<Instant> queued() {
        if (waitingForDump().isPresent() || zenodo.startedAt().isPresent()) return Optional.empty();

        return Optional.of(zenodo.createdAt());
    }

    public Optional<Instant> waitingForDump() {
        if (run != null && run.finishedAt().isPresent()) return Optional.empty();

        return Optional.of(zenodo.createdAt());
    }

}
