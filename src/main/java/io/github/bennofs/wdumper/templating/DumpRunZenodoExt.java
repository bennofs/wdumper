package io.github.bennofs.wdumper.templating;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import io.github.bennofs.wdumper.model.DumpRunZenodo;
import io.github.bennofs.wdumper.model.Progress;
import io.github.bennofs.wdumper.web.ProgressEstimator;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Optional;

public class DumpRunZenodoExt {
    private final UrlBuilder urls;
    private final DumpRunZenodo dumpRunZenodo;
    private final ProgressEstimator progressEstimator;

    public DumpRunZenodoExt(UrlBuilder urls, DumpRunZenodo dump, ProgressEstimator progressEstimator) {
        this.urls = urls;
        this.dumpRunZenodo = dump;
        this.progressEstimator = progressEstimator;
    }

    public interface VersionDetails {
        String releaseName();
        String releaseLink();

        static Optional<VersionDetails> parse(String repo, String version) {
            if (version == null || version.isEmpty()) return Optional.empty();

            if (version.startsWith("release-")) {
                return Optional.of(new GithubRelease(repo, version.substring("release-".length())));
            }
            return Optional.of(new GithubCommit(repo, version));
        }
    }

    public static class GithubCommit implements VersionDetails {
        private final String repo;
        private final String commit;

        public GithubCommit(String repo, String commit) {
            this.repo = repo;
            this.commit = commit;
        }

        public String releaseName() {
            return "git-" + this.commit.substring(0, 10);
        }

        public String releaseLink() {
            return this.repo + "/commit/" + this.commit;
        }
    }

    public static class GithubRelease implements VersionDetails {
        private final String repo;
        private final String name;

        public GithubRelease(String repo, String name) {
            this.repo = repo;
            this.name = name;
        }

        public String releaseName() {
            return name;
        }

        public String releaseLink() {
            return repo + "/releases/" + URLEncoder.encode(name, Charset.defaultCharset());
        }
    }

    public String link() {
        return urls.urlPathString("dump/" + dumpRunZenodo.dump.id());
    }

    public Optional<String> linkDownload() {
        if (dumpRunZenodo.finishedAt().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(urls.urlPathString("download/" + dumpRunZenodo.dump.id()));
    }

    public Optional<Progress> processing() {
        if (dumpRunZenodo.run == null || dumpRunZenodo.startedAt().isEmpty() || dumpRunZenodo.finishedAt().isPresent()) {
            return Optional.empty();
        }

        return progressEstimator.estimate(dumpRunZenodo.run);
    }

    public Optional<ZenodoStatus> zenodoSandboxStatus() {
        return Optional.ofNullable(dumpRunZenodo.zenodoSandbox)
                .map(z -> new ZenodoStatus(dumpRunZenodo.run, dumpRunZenodo.dump, z));
    }

    public Optional<ZenodoStatus> zenodoReleaseStatus() {
        return Optional.ofNullable(dumpRunZenodo.zenodoRelease)
                .map(z -> new ZenodoStatus(dumpRunZenodo.run, dumpRunZenodo.dump, z));
    }

    private final static String TOOL_REPO = "https://github.com/bennofs/wdumper";
    public Optional<VersionDetails> toolVersionDetails() {
        return Optional.ofNullable(this.dumpRunZenodo.run).flatMap(r -> VersionDetails.parse(TOOL_REPO, r.toolVersion()));
    }

    private final static String WDTK_REPO = "https://github.com/Wikidata/Wikidata-Toolkit";
    public Optional<VersionDetails> wdtkVersionDetails() {
        return Optional.ofNullable(this.dumpRunZenodo.run).flatMap(r -> VersionDetails.parse(WDTK_REPO, r.wdtkVersion()));
    }

    final static ObjectMapper MAPPER = new ObjectMapper();
    static {
        MAPPER.registerModule(new Jdk8Module());
        MAPPER.registerModule(new JavaTimeModule());
        MAPPER.registerModule(new ParameterNamesModule());
    }
    public String specPretty() {
        try {
            final JsonNode node = MAPPER.readTree(this.dumpRunZenodo.dump.spec());
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (IOException e) {
            return this.dumpRunZenodo.dump.spec();
        }
    }
}
