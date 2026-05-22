package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Rule that requires .gitkeep or real files in empty directories.
 */
public enum RequireKeepfileInEmptyDirectoriesRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireKeepfileInEmptyDirectories";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("directories")).stream()
                .flatMap(dir -> validateKeepFile(root, dir, severity).stream())
                .toList();
    }

    private List<Finding> validateKeepFile(Path root, String dir, String severity) {
        Path dirPath = root.resolve(dir);
        List<Finding> findings;
        if (HarnessCheckHelper.isSafeDirectory(root, dirPath)) {
            try (Stream<Path> stream = Files.list(dirPath)) {
                List<Path> realFiles = stream
                        .filter(f -> !Files.isSymbolicLink(f) && !f.getFileName().toString().equals(".gitkeep"))
                        .toList();
                if (realFiles.isEmpty() && !HarnessCheckHelper.isSafeRegularFile(root, dirPath.resolve(".gitkeep"))) {
                    findings = List.of(new Finding(severity, CATEGORY, "empty directory must keep placeholder or real files: " + dir));
                } else {
                    findings = List.of();
                }
            } catch (IOException e) {
                findings = List.of();
            }
        } else {
            findings = List.of();
        }
        return findings;
    }
}
