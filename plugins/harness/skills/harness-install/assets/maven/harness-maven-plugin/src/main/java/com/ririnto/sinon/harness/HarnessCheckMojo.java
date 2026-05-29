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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Maven goal that validates installed Claude repository harness assets.
 */
@Mojo(name = "check", threadSafe = true)
public final class HarnessCheckMojo extends AbstractMojo {
    private static final Path MANIFEST_PATH = Path.of("docs", "harness", "manifest.json");

    /**
     * Executes harness validation for the current Maven invocation root.
     */
    @Override
    public void execute() throws MojoExecutionException {
        StaticJavaParser.setConfiguration(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));
        try {
            final Path root = currentRoot();
            final JsonNode manifest = loadManifest(root);
            executeValidation(root, manifest);
        } catch (IOException error) {
            throw new MojoExecutionException("validation failed", error);
        }
    }

    private void executeValidation(Path root, JsonNode manifest) throws IOException, MojoExecutionException {
        final RuleContext ctx = new DefaultRuleContext(root, new DefaultManifest(manifest));
        final Set<String> knownCategories = Arrays.stream(HarnessCheck.values())
                .map(HarnessCheck::category)
                .collect(Collectors.toUnmodifiableSet());
        final List<Finding> sourceRootFindings = collectSourceRootFindings(ctx);
        final List<Finding> findings = Stream.concat(
                manifest.propertyNames().stream()
                        .filter(key -> !knownCategories.contains(key) && !Set.of("name", "description", "$schema", "seedFiles", "generatedArtifacts", "harnessEvolution", "teamPatterns").contains(key))
                        .map(key -> Finding.of("WARN", "manifestSchema", "unknown manifest key: " + key)),
                Stream.concat(
                        sourceRootFindings.stream(),
                        Arrays.stream(HarnessCheck.values())
                                .filter(check -> check.applies(ctx))
                                .flatMap(check -> validateCheck(ctx, check).stream())))
                .sorted((a, b) -> {
                    final int severityOrder = severityRank(b.severity()) - severityRank(a.severity());
                    return severityOrder != 0 ? severityOrder : a.message().compareTo(b.message());
                })
                .toList();
        for (final String line : FindingReporter.renderFindings(root, findings)) {
            getLog().info(line);
        }
        if (findings.stream().anyMatch(f -> "ERROR".equals(f.severity()))) {
            throw new MojoExecutionException("Harness validation failed");
        }
    }

    /**
     * Collects source-root safety findings across all applicable rule categories.
     */
    private static List<Finding> collectSourceRootFindings(RuleContext ctx) throws MojoExecutionException {
        final List<Finding> findings = new java.util.ArrayList<>();
        final Set<String> categories = Arrays.stream(HarnessCheck.values())
                .filter(check -> check.applies(ctx))
                .map(HarnessCheck::category)
                .collect(Collectors.toUnmodifiableSet());
        for (final String category : categories) {
            findings.addAll(ctx.stackSourceFindings(category));
        }
        return findings.stream().distinct().toList();
    }

    /**
     * Returns numeric rank for severity ordering (ERROR > WARN > INFO).
     */
    private static int severityRank(String severity) {
        return switch (severity) {
            case "ERROR" -> 3;
            case "WARN" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
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

    /**
     * Runs one check and turns check-level failures into findings.
     */
    private static List<Finding> validateCheck(RuleContext ctx, HarnessCheck check) {
        try {
            return check.validate(ctx).stream().toList();
        } catch (MojoExecutionException error) {
            return List.of(Finding.of("ERROR", check.category(), "failed to validate " + check.category() + ": " + error.getMessage()));
        }
    }
}
