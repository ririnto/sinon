package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires specific headings in documentation files.
 */
public class RequireDocHeadingsRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireDocHeadings";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        List<String> requiredFiles = HarnessCheckHelper.extractPaths(manifest.get("requireFilesExist").get("parameters").get("paths"));
        List<String> headings = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("headings"));
        JsonNode sourceFilter = catNode.get("parameters").get("sourceFilter");
        String prefix = sourceFilter.get("prefix").asText();
        String suffix = sourceFilter.get("suffix").asText();
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return requiredFiles.stream()
                .filter(f -> f.startsWith(prefix) && f.endsWith(suffix))
                .flatMap(f -> validateHeadings(root, f, headings, severity).stream())
                .toList();
    }

    private List<Finding> validateHeadings(Path root, String file, List<String> headings, String severity) throws MojoExecutionException {
        Path filePath = root.resolve(file);
        if (!HarnessCheckHelper.isSafeRegularFile(root, filePath)) {
            return List.of();
        }
        String text = HarnessCheckHelper.readFile(root, filePath);
        return headings.stream()
                .filter(h -> !text.contains(h))
                .map(h -> new Finding(severity, CATEGORY, "doc missing " + h + ": " + file))
                .toList();
    }
}
