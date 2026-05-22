package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maven goal that validates installed Claude repository harness assets.
 */
@Mojo(name = "validate", threadSafe = true)
public final class HarnessValidateMojo extends AbstractMojo {
    private static final String MANIFEST_PATH = "docs/harness/manifest.json";

    /**
     * Executes harness validation for the current Maven invocation root.
     */
    @Override
    public void execute() throws MojoExecutionException {
        Path root = currentRoot();
        JsonNode manifest = loadManifest(root);
        if (manifest == null) {
            return;
        }

        Set<String> knownCategories = Arrays.stream(HarnessCheck.values())
                .map(HarnessCheck::category)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> knownMetadataKeys = Set.of("name", "description", "$schema", "seedFiles", "generatedArtifacts", "harnessEvolution", "teamPatterns");

        Stream.of(manifest.fieldNames().spliterator(), false)
                .filter(key -> !knownCategories.contains(key) && !knownMetadataKeys.contains(key))
                .forEach(key -> getLog().warn("unknown manifest key: " + key));

        List<Finding> sorted = Arrays.stream(HarnessCheck.values())
                .filter(check -> check.applies(manifest))
                .flatMap(check -> {
                    try {
                        return check.validate(root, manifest).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .sorted((a, b) -> {
                    int severityOrder = severityRank(b.severity()) - severityRank(a.severity());
                    return severityOrder != 0 ? severityOrder : a.message().compareTo(b.message());
                })
                .toList();

        for (Finding finding : sorted) {
            if ("ERROR".equals(finding.severity())) {
                getLog().error("[ERROR] " + finding.message());
            } else if ("WARN".equals(finding.severity())) {
                getLog().warn("[WARN] " + finding.message());
            } else {
                getLog().info("[INFO] " + finding.message());
            }
        }

        if (sorted.stream().anyMatch(f -> f.severity().equals("ERROR"))) {
            throw new MojoExecutionException("Harness validation failed");
        }
        getLog().info("Harness validation passed");
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
    private static Path currentRoot() throws MojoExecutionException {
        try {
            return Path.of(System.getProperty("user.dir")).toRealPath();
        } catch (IOException error) {
            throw new MojoExecutionException("failed to resolve current project root", error);
        }
    }

    /**
     * Loads and parses manifest.json; returns null if not found or unparseable.
     */
    private static JsonNode loadManifest(Path root) throws MojoExecutionException {
        Path manifestPath = root.resolve(MANIFEST_PATH);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            String raw = Files.readString(manifestPath, StandardCharsets.UTF_8);
            return new ObjectMapper().readTree(raw);
        } catch (IOException e) {
            return null;
        }
    }
}
