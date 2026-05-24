package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that requires specific template groups to exist.
 */
public enum TemplateGroupsRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "templateGroups";
    }
    private static final String CATEGORY = "templateGroups";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final String targetRoot = catNode.get("parameters").get("targetRoot").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("groups")).stream()
                .filter(g -> !HarnessCheckHelper.isSafeDirectory(root, root.resolve(targetRoot).resolve(g)))
                .map(g -> new Finding(severity, CATEGORY, "missing template group: " + targetRoot + "/" + g))
                .toList();
    }
}
