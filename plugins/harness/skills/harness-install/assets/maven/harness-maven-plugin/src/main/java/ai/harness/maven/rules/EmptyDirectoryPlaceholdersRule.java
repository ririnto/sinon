package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires .gitkeep or real files in empty directories.
 */
public enum EmptyDirectoryPlaceholdersRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "emptyDirectoryPlaceholders";
    }
    private static final String CATEGORY = "emptyDirectoryPlaceholders";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("directories")).stream()
                .flatMap(dir -> validateKeepFile(root, dir, severity).stream())
                .toList();
    }

    private List<Finding> validateKeepFile(Path root, String dir, String severity) {
        final Path dirPath = root.resolve(dir);
        return Stream.of(dirPath)
                .filter(p -> HarnessCheckHelper.isSafeDirectory(root, p))
                .flatMap(p -> {
                    try (final Stream<Path> stream = Files.list(p)) {
                        final List<Path> realFiles = stream
                                .filter(f -> !Files.isSymbolicLink(f))
                                .filter(f -> !f.getFileName().toString().equals(".gitkeep"))
                                .toList();
                        return realFiles.isEmpty() && !HarnessCheckHelper.isSafeRegularFile(root, p.resolve(".gitkeep"))
                                ? Stream.of(new Finding(severity, CATEGORY, "empty directory must keep placeholder or real files: " + dir))
                                : Stream.empty();
                    } catch (IOException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
