package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires Kotlin files to have exactly one top-level declaration.
 */
public enum KotlinTopLevelDeclarationCountRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "kotlinTopLevelDeclarationCount";
    }
    private static final String CATEGORY = "kotlinTopLevelDeclarationCount";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final List<String> directories = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("directories"));
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return directories.stream()
                .flatMap(dir -> {
                    final Path dirPath = root.resolve(dir);
                    try {
                        return HarnessCheckHelper.safeFileOrWalk(root, dirPath).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .filter(f -> f.getFileName().toString().endsWith(".kt"))
                .flatMap(file -> {
                    try {
                        return validateKotlinDeclarations(root, file, severity).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> validateKotlinDeclarations(Path root, Path file, String severity) throws MojoExecutionException {
        final String text = HarnessCheckHelper.readFile(root, file);
        final long count = Stream.of(text.split("\n"))
                .map(String::trim)
                .filter(trimmed -> trimmed.startsWith("class ") || trimmed.startsWith("fun ") || trimmed.startsWith("interface ") ||
                        trimmed.startsWith("object ") || trimmed.startsWith("enum class "))
                .count();
        return Stream.of(count)
                .filter(c -> c != 1)
                .map(c -> new Finding(severity, CATEGORY, "Kotlin file must have exactly 1 top-level declaration: " + root.relativize(file)))
                .toList();
    }
}
