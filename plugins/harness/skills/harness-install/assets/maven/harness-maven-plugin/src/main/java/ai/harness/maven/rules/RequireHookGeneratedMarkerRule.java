package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Rule that requires hook files to contain a generated marker.
 */
public enum RequireHookGeneratedMarkerRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireHookGeneratedMarker";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        List<String> hooks = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks"));
        String markerTemplate = catNode.get("parameters").get("markerTemplate").asText();
        String placeholderForbidden = catNode.get("parameters").get("placeholderForbidden").asText();
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return hooks.stream()
                .flatMap(hook -> validateMarker(root, hook, markerTemplate, placeholderForbidden, severity).stream())
                .toList();
    }

    private List<Finding> validateMarker(Path root, String hook, String markerTemplate, String placeholderForbidden, String severity) throws MojoExecutionException {
        Path hookPath = root.resolve(hook);
        if (!HarnessCheckHelper.isSafeRegularFile(root, hookPath)) {
            return List.of();
        }
        String text = HarnessCheckHelper.readFile(root, hookPath);
        String expectedMarker = markerTemplate.replace("{name}", hookPath.getFileName().toString());
        return Stream.of(
                text.contains(expectedMarker) ? Stream.empty() : Stream.of(new Finding(severity, CATEGORY, hook + " must contain generated marker '" + expectedMarker + "'")),
                text.contains(placeholderForbidden) ? Stream.of(new Finding(severity, CATEGORY, hook + " still contains packaging placeholder text")) : Stream.empty()
        ).flatMap(s -> s).toList();
    }
}
