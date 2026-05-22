package ai.harness.maven;

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
        List<String> directories = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("directories"));
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return directories.stream()
                .flatMap(dir -> validateKeepFile(root, dir, severity).stream())
                .toList();
    }

    private List<Finding> validateKeepFile(Path root, String dir, String severity) {
        Path dirPath = root.resolve(dir);
        if (!HarnessCheckHelper.isSafeDirectory(root, dirPath)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dirPath)) {
            List<Path> realFiles = stream
                    .filter(f -> !Files.isSymbolicLink(f) && !f.getFileName().toString().equals(".gitkeep"))
                    .toList();
            return realFiles.isEmpty() && !HarnessCheckHelper.isSafeRegularFile(root, dirPath.resolve(".gitkeep"))
                    ? List.of(new Finding(severity, CATEGORY, "empty directory must keep placeholder or real files: " + dir))
                    : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }
}
