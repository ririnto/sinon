package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that forbids unchecked tasks in completed plan documents.
 */
public class ForbidUncheckedTasksUnderRule implements HarnessCheckRule {
    private static final String CATEGORY = "forbidUncheckedTasksUnder";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        String directory = catNode.get("parameters").get("directory").asText();
        String uncheckedPattern = catNode.get("parameters").get("uncheckedTaskPattern").asText();
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        Path dirPath = root.resolve(directory);
        if (!HarnessCheckHelper.isSafeDirectory(root, dirPath)) {
            return List.of();
        }
        List<Path> files = HarnessCheckHelper.safeFileOrWalk(root, dirPath).stream()
                .filter(f -> f.getFileName().toString().endsWith(".md") && !f.getFileName().toString().equals(".gitkeep"))
                .toList();
        Pattern pattern = Pattern.compile(uncheckedPattern);
        return files.stream()
                .flatMap(file -> {
                    try {
                        String text = HarnessCheckHelper.readFile(root, file);
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
