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
import java.util.List;
import java.util.Arrays;

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
            final List<MojoExecutionException> formatFailures = new java.util.ArrayList<>();
            final List<String> modified = buildModifiedPaths(root, ctx, formatFailures);
            if (!modified.isEmpty()) {
                getLog().info("formatted: " + modified.size());
                modified.forEach(path -> getLog().info("  " + path));
            } else {
                getLog().info("no files formatted");
            }

            final List<Finding> remainingFindings = buildRemainingFindings(ctx);
            getLog().info("remaining findings after format:");
            FindingReporter.renderFindings(root, remainingFindings).forEach(getLog()::info);

            if (!formatFailures.isEmpty()) {
                formatFailures.forEach(failure -> getLog().warn(failure.getMessage()));
            }
            if (remainingFindings.stream().anyMatch(finding -> "ERROR".equals(finding.severity())) || !formatFailures.isEmpty()) {
                throw new MojoExecutionException("Harness validation failed after format");
            }
        } catch (IOException error) {
            throw new MojoExecutionException("formatting failed", error);
        }
    }


    /**
     * Collects validation findings remaining after formatting,
     * including source-root safety findings.
     */
    private List<Finding> buildRemainingFindings(RuleContext ctx) {
        final List<Finding> findings = new java.util.ArrayList<>();
        findings.addAll(collectSourceRootFindings(ctx));
        for (final HarnessCheck check : HarnessCheck.values()) {
            if (check.applies(ctx)) {
                try {
                    findings.addAll(check.validate(ctx));
                } catch (MojoExecutionException error) {
                    findings.add(Finding.of("ERROR", check.category(), "failed to validate " + check.category() + ": " + error.getMessage()));
                }
            }
        }
        return findings
                .stream()
                .distinct()
                .toList();
    }

    /**
     * Collects source-root safety findings across all applicable rule categories.
     */
    private static List<Finding> collectSourceRootFindings(RuleContext ctx) {
        final List<Finding> findings = new java.util.ArrayList<>();
        final java.util.Set<String> categories = Arrays.stream(HarnessCheck.values())
                .filter(check -> check.applies(ctx))
                .map(HarnessCheck::category)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        for (final String category : categories) {
            try {
                findings.addAll(ctx.stackSourceFindings(category));
            } catch (MojoExecutionException error) {
                findings.add(Finding.of("ERROR", category, "failed to collect source-root findings: " + error.getMessage()));
            }
        }
        return findings.stream().distinct().toList();
    }

    /**
     * Collects formatted file paths from applicable checks, relativized and sorted.
     */
    private List<String> buildModifiedPaths(Path root, RuleContext ctx, List<MojoExecutionException> failures) throws MojoExecutionException {
        final List<Path> modified = new java.util.ArrayList<>();
        for (final HarnessCheck check : HarnessCheck.values()) {
            if (check.applies(ctx)) {
                try {
                    modified.addAll(check.format(ctx));
                } catch (MojoExecutionException error) {
                    failures.add(error);
                }
            }
        }
        return modified.stream()
                .map(path -> root.relativize(path).toString())
                .distinct()
                .sorted()
                .toList();
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
        return new ObjectMapper().readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
    }
}
