package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Rule that requires specific content in documentation files.
 */
public class RequireDocContentRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireDocContent";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        JsonNode checksNode = catNode.get("parameters").get("checks");
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return Stream.of(checksNode.spliterator(), false)
                .flatMap(check -> validateCheck(root, check, severity).stream())
                .toList();
    }

    private List<Finding> validateCheck(Path root, JsonNode check, String severity) throws MojoExecutionException {
        List<String> files = HarnessCheckHelper.extractPaths(check.get("files"));
        List<String> containsAll = HarnessCheckHelper.extractPaths(check.get("containsAll"));
        String failureMessage = check.get("failureMessage").asText();
        String combined = files.stream()
                .map(f -> {
                    try {
                        return HarnessCheckHelper.readFile(root, root.resolve(f));
                    } catch (MojoExecutionException e) {
                        return "";
                    }
                })
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return containsAll.stream().allMatch(combined::contains)
                ? List.of()
                : List.of(new Finding(severity, CATEGORY, failureMessage));
    }
}
