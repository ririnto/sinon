package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.List;

/**
 * Rule that requires specified files to exist in the project.
 */
public class RequireFilesExistRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireFilesExist";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        List<String> paths = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("paths"));
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return paths.stream()
                .filter(path -> !HarnessCheckHelper.isSafeRegularFile(root, root.resolve(path)))
                .map(path -> new Finding(severity, CATEGORY, "missing file: " + path))
                .toList();
    }
}
