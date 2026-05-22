package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires hook files to contain stage markers.
 */
public enum RequireHookStageRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireHookStage";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final JsonNode stages = catNode.get("parameters").get("stages");
        final JsonNode stackStages = stages.get("maven");
        final String markerTemplate = catNode.get("parameters").get("markerTemplate").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return Stream.ofNullable(stackStages)
                .flatMap(ss -> Stream.of(ss.fieldNames().spliterator(), false)
                        .flatMap(hookName -> validateStage(root, hookName, ss.get(hookName).asText(), markerTemplate, severity).stream()))
                .toList();
    }

    private List<Finding> validateStage(Path root, String hookName, String expectedStage, String markerTemplate, String severity) throws MojoExecutionException {
        final String hookPath = "docs/harness/git-hooks/" + hookName;
        final Path hook = root.resolve(hookPath);
        return Stream.of(hook)
                .filter(p -> HarnessCheckHelper.isSafeRegularFile(root, p))
                .flatMap(p -> {
                    try {
                        final String text = HarnessCheckHelper.readFile(root, p);
                        final String expectedMarker = markerTemplate.replace("{stage}", expectedStage);
                        return text.contains(expectedMarker)
                                ? Stream.empty()
                                : Stream.of(new Finding(severity, CATEGORY, hookPath + " must contain stage marker '" + expectedMarker + "'"));
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
