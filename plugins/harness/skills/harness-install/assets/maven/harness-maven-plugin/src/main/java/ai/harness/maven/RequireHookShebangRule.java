package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.List;

/**
 * Rule that requires hook files to start with a specific shebang.
 */
public class RequireHookShebangRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireHookShebang";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        List<String> hooks = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks"));
        String expected = catNode.get("parameters").get("expectedShebang").asText();
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return hooks.stream()
                .flatMap(hook -> validateHookShebang(root, hook, expected, severity).stream())
                .toList();
    }

    private List<Finding> validateHookShebang(Path root, String hook, String expected, String severity) throws MojoExecutionException {
        Path hookPath = root.resolve(hook);
        return HarnessCheckHelper.isSafeRegularFile(root, hookPath) && !HarnessCheckHelper.readFile(root, hookPath).startsWith(expected)
                ? List.of(new Finding(severity, CATEGORY, hook + " must start with " + expected))
                : List.of();
    }
}
