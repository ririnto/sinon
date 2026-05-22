package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that forbids unchecked tasks in completed plan documents.
 */
public enum ForbidUncheckedTasksUnderRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidUncheckedTasksUnder";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final String directory = catNode.get("parameters").get("directory").asText();
        final String uncheckedPattern = catNode.get("parameters").get("uncheckedTaskPattern").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final Path dirPath = root.resolve(directory);
        if (!HarnessCheckHelper.isSafeDirectory(root, dirPath)) {
            return List.of();
        }
        final Pattern pattern = Pattern.compile(uncheckedPattern);
        return HarnessCheckHelper.safeFileOrWalk(root, dirPath).stream()
                .filter(f -> f.getFileName().toString().endsWith(".md") && !f.getFileName().toString().equals(".gitkeep"))
                .flatMap(file -> {
                    try {
                        final String text = HarnessCheckHelper.readFile(root, file);
                        return pattern.matcher(text).find()
                                ? Stream.of(new Finding(severity, CATEGORY, "completed plan has unchecked tasks: " + root.relativize(file)))
                                : Stream.empty();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
