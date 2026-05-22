package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Rule that requires hook files to be executable.
 */
public class RequireHookExecutableRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireHookExecutable";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        List<String> hooks = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks"));
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return hooks.stream()
                .filter(hook -> HarnessCheckHelper.isSafeRegularFile(root, root.resolve(hook)) && !Files.isExecutable(root.resolve(hook)))
                .map(hook -> new Finding(severity, CATEGORY, hook + " must be executable"))
                .toList();
    }
}
