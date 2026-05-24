package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that requires specified directories to exist in the project.
 */
public enum DirectoryPresenceRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "directoryPresence";
    }
    private static final String CATEGORY = "directoryPresence";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("paths")).stream()
                .filter(path -> !HarnessCheckHelper.isSafeDirectory(root, root.resolve(path)))
                .map(path -> new Finding(severity, CATEGORY, "missing directory: " + path))
                .toList();
    }
}
