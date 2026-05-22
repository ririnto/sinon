package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires hook files to contain stage markers.
 */
public class RequireHookStageRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireHookStage";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        JsonNode stages = catNode.get("parameters").get("stages");
        JsonNode stackStages = stages.get("maven");
        if (stackStages == null) {
            return List.of();
        }
        String markerTemplate = catNode.get("parameters").get("markerTemplate").asText();
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return Stream.of(stackStages.fieldNames().spliterator(), false)
                .flatMap(hookName -> validateStage(root, hookName, stackStages.get(hookName).asText(), markerTemplate, severity).stream())
                .toList();
    }

    private List<Finding> validateStage(Path root, String hookName, String expectedStage, String markerTemplate, String severity) throws MojoExecutionException {
        String hookPath = "docs/harness/git-hooks/" + hookName;
        Path hook = root.resolve(hookPath);
        if (!HarnessCheckHelper.isSafeRegularFile(root, hook)) {
            return List.of();
        }
        String text = HarnessCheckHelper.readFile(root, hook);
        String expectedMarker = markerTemplate.replace("{stage}", expectedStage);
        return text.contains(expectedMarker)
                ? List.of()
                : List.of(new Finding(severity, CATEGORY, hookPath + " must contain stage marker '" + expectedMarker + "'"));
    }
}
