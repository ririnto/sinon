package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
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
        final JsonNode catNode = manifest.get(CATEGORY);
        final String markerTemplate = catNode.get("parameters").get("markerTemplate").asText();
        final String placeholderForbidden = catNode.get("parameters").get("placeholderForbidden").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks")).stream()
                .flatMap(hook -> validateMarker(root, hook, markerTemplate, placeholderForbidden, severity).stream())
                .toList();
    }

    private List<Finding> validateMarker(Path root, String hook, String markerTemplate, String placeholderForbidden, String severity) throws MojoExecutionException {
        final Path hookPath = root.resolve(hook);
        return Stream.of(hookPath)
                .filter(p -> HarnessCheckHelper.isSafeRegularFile(root, p))
                .flatMap(p -> {
                    try {
                        final String text = HarnessCheckHelper.readFile(root, p);
                        final String expectedMarker = markerTemplate.replace("{name}", p.getFileName().toString());
                        return Stream.of(
                                text.contains(expectedMarker) ? Stream.empty() : Stream.of(new Finding(severity, CATEGORY, hook + " must contain generated marker '" + expectedMarker + "'")),
                                text.contains(placeholderForbidden) ? Stream.of(new Finding(severity, CATEGORY, hook + " still contains packaging placeholder text")) : Stream.empty()
                        ).flatMap(s -> s);
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
