package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.List;

/**
 * Rule that requires specific template groups to exist.
 */
public class RequireTemplateGroupsRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireTemplateGroups";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        String targetRoot = catNode.get("parameters").get("targetRoot").asText();
        List<String> groups = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("groups"));
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return groups.stream()
                .filter(g -> !HarnessCheckHelper.isSafeDirectory(root, root.resolve(targetRoot).resolve(g)))
                .map(g -> new Finding(severity, CATEGORY, "missing template group: " + targetRoot + "/" + g))
                .toList();
    }
}
