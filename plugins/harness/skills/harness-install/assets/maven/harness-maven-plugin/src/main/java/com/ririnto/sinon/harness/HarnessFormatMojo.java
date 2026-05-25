package com.ririnto.sinon.harness;

import com.ririnto.sinon.harness.core.DefaultManifest;
import com.ririnto.sinon.harness.core.DefaultRuleContext;
import com.ririnto.sinon.harness.core.RuleContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Maven goal that auto-formats installed Claude repository harness assets.
 */
@Mojo(name = "format", threadSafe = true)
public final class HarnessFormatMojo extends AbstractMojo {
    private static final Path MANIFEST_PATH = Path.of("docs", "harness", "manifest.json");

    /**
     * Executes harness formatting for the current Maven invocation root.
     */
    @Override
    public void execute() throws MojoExecutionException {
        StaticJavaParser.setConfiguration(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        try {
            final Path root = currentRoot();
            final RuleContext ctx = new DefaultRuleContext(root, new DefaultManifest(loadManifest(root)));
            final List<String> modified = buildModifiedPaths(root, ctx);
            if (!modified.isEmpty()) {
                getLog().info("formatted: " + modified.size());
                modified.forEach(path -> getLog().info("  " + path));
            } else {
                getLog().info("no files formatted");
            }
        } catch (IOException error) {
            throw new MojoExecutionException("formatting failed", error);
        }
    }

    /**
     * Collects formatted file paths from applicable checks, relativized and sorted.
     */
    private List<String> buildModifiedPaths(Path root, RuleContext ctx) throws MojoExecutionException {
        final java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        for (final HarnessCheck check : HarnessCheck.values()) {
            if (check.applies(ctx)) {
                check.format(ctx).stream()
                        .map(path -> root.relativize(path).toString())
                        .forEach(paths::add);
            }
        }
        return paths.stream().sorted().toList();
    }

    /**
     * Returns the current working directory as an absolute Path.
     */
    private static Path currentRoot() throws IOException {
        return Path.of(System.getProperty("user.dir")).toRealPath();
    }

    /**
     * Loads and parses manifest.json.
     */
    private static JsonNode loadManifest(Path root) throws IOException {
        final Path manifestPath = root.resolve(MANIFEST_PATH);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("missing manifest: " + MANIFEST_PATH.toString());
        }
        final String raw = Files.readString(manifestPath, StandardCharsets.UTF_8);
        return new ObjectMapper().readTree(raw);
    }
}
