package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that requires hook files to be executable.
 */
public enum RequireHookExecutableRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireHookExecutable";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks")).stream()
                .filter(hook -> HarnessCheckHelper.isSafeRegularFile(root, root.resolve(hook)) && !Files.isExecutable(root.resolve(hook)))
                .map(hook -> new Finding(severity, CATEGORY, hook + " must be executable"))
                .toList();
    }
}
