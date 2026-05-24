package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires specific headings in documentation files.
 */
public enum DocHeadingsRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "docHeadings";
    }
    private static final String CATEGORY = "docHeadings";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final List<String> headings = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("headings"));
        final JsonNode sourceFilter = catNode.get("parameters").get("sourceFilter");
        final String prefix = sourceFilter.get("prefix").asText();
        final String suffix = sourceFilter.get("suffix").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(manifest.get("filePresence").get("parameters").get("paths")).stream()
                .filter(f -> f.startsWith(prefix))
                .filter(f -> f.endsWith(suffix))
                .flatMap(f -> validateHeadings(root, f, headings, severity).stream())
                .toList();
    }

    private List<Finding> validateHeadings(Path root, String file, List<String> headings, String severity) {
        final Path filePath = root.resolve(file);
        return Stream.of(filePath)
                .filter(p -> HarnessCheckHelper.isSafeRegularFile(root, p))
                .map(p -> {
                    try {
                        return HarnessCheckHelper.readFile(root, p);
                    } catch (MojoExecutionException e) {
                        return "";
                    }
                })
                .flatMap(text -> headings.stream()
                        .filter(h -> !text.contains(h))
                        .map(h -> new Finding(severity, CATEGORY, "doc missing " + h + ": " + file)))
                .toList();
    }
}
