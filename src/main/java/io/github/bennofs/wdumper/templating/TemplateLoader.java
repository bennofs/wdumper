package io.github.bennofs.wdumper.templating;

import com.google.common.collect.ImmutableMap;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TemplateLoader {
    private final UrlBuilder urls;
    private final Mustache.Compiler mustacheCompiler;
    private final Map<String, Template> templateCache = new ConcurrentHashMap<>();
    private final ImmutableMap<String, String> resources;
    private final Config config;

    public interface Config {
        Path staticDir();
        boolean isDevelopment();
    }


    TemplateLoader(UrlBuilder urls, Mustache.Compiler mustacheCompiler, ImmutableMap<String, String> resources, Config config) {
        this.urls = urls;
        this.mustacheCompiler = mustacheCompiler;
        this.resources = resources;
        this.config = config;
    }

    public static TemplateLoader create(UrlBuilder urls, Mustache.Compiler mustacheCompiler, Config config) throws IOException {
        return new TemplateLoader(urls, mustacheCompiler, loadResources(urls, config), config);
    }

    private Template loadTemplate(String name) {
        if (!config.isDevelopment()) {
            return templateCache.computeIfAbsent(name, mustacheCompiler::loadTemplate);
        } else {
            return mustacheCompiler.loadTemplate(name);
        }
    }

    public byte[] render(String name, String view, Object context) throws Exception {
        final Template content = loadTemplate(name);
        final Template layout = loadTemplate("base.mustache");
        return new MustacheResponse(layout,
                Map.of(
                        "resources", resources,
                        "path", (Mustache.Lambda) (frag, out) -> out.write(urls.urlPathString(frag.decompile())),
                        "view", view,
                        "isDevelopment", config.isDevelopment()
                ),
                content,
                context,
                name).render();
    }


    public byte[] render(String name, String view) throws Exception {
        return render(name, view, Collections.emptyMap());
    }

    private static ImmutableMap<String, String> loadResources(UrlBuilder urls, Config config) throws IOException {
        final Path from = config.staticDir();
        final HashMap<String, String> resources = new HashMap<>();
        resources.put("url/api", urls.urlPrefixString(""));
        resources.put("url/root", urls.urlPrefixString(""));

        final boolean preferMinimized = !config.isDevelopment();
        try (final var files = Files.walk(from)) {
            files.forEach(path -> {
                final String relative = from.relativize(path).toString();

                // add directories as url/dirname resource
                if (Files.isDirectory(path)) {
                    resources.put("url/" + relative, urls.urlPathString("static" + "/" + relative));
                    return;
                }

                final String[] components = relative.split("\\.");
                final boolean isMinimized;
                final String type;
                if (components.length == 2) {
                    isMinimized = false;
                    type = components[1];
                } else if (components.length == 3 && components[1].equals("min")) {
                    isMinimized = true;
                    type = components[2];
                } else { // unknown resource type, ignore
                    return;
                }

                // check if this is a supported resource type
                final String category;
                if (type.equals("css") || type.equals("js")) {
                    category = type;
                } else if (type.equals("svg") || type.equals("png")) {
                    category = "img";
                } else { // unknown resource type, ignore
                    return;
                }

                final String key = category + "/" + components[0];
                if (resources.containsKey(key) && isMinimized != preferMinimized) { // we already have a preferred one
                    return;
                }

                resources.put(key, urls.urlPathString("static" + "/" + relative));
            });
        }

        return ImmutableMap.copyOf(resources);
    }
}
