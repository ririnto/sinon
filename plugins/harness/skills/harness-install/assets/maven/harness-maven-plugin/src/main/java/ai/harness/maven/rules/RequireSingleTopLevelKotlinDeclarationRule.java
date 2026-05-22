package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Rule that requires Kotlin files to have exactly one top-level declaration.
 */
public enum RequireSingleTopLevelKotlinDeclarationRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireSingleTopLevelKotlinDeclaration";

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
                .flatMap(dir -> {
                    try {
                        Path dirPath = root.resolve(dir);
                        return HarnessCheckHelper.safeFileOrWalk(root, dirPath).stream()
                                .filter(f -> f.getFileName().toString().endsWith(".kt"))
                                .flatMap(file -> validateKotlinDeclarations(root, file, severity).stream());
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> validateKotlinDeclarations(Path root, Path file, String severity) throws MojoExecutionException {
        String text = HarnessCheckHelper.readFile(root, file);
        int count = 0;
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("class ") || trimmed.startsWith("fun ") || trimmed.startsWith("interface ") ||
                trimmed.startsWith("object ") || trimmed.startsWith("enum class ")) {
                count++;
            }
        }
        return count != 1
                ? List.of(new Finding(severity, CATEGORY, "Kotlin file must have exactly 1 top-level declaration: " + root.relativize(file)))
                : List.of();
    }
}
