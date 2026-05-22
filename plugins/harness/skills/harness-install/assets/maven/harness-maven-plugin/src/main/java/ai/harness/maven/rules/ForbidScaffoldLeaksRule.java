package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that forbids scaffold template patterns in active assets.
 */
public enum ForbidScaffoldLeaksRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidScaffoldLeaks";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final JsonNode scope = catNode.get("parameters").get("scope");
        final List<String> excluded = HarnessCheckHelper.extractPaths(scope.get("excludedSubtrees"));
        final List<String> extensions = HarnessCheckHelper.extractPaths(scope.get("extensions"));
        final JsonNode patternsNode = catNode.get("parameters").get("patterns");
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final List<Path> excludedPaths = excluded.stream().map(root::resolve).toList();
        final String extRegex = extensions.isEmpty() ? ".*" : ".*\\.(" + String.join("|", extensions.stream().map(Pattern::quote).toList()) + ")$";
        final Pattern extPattern = Pattern.compile(extRegex);
        return HarnessCheckHelper.extractPaths(scope.get("bases")).stream()
                .flatMap(baseName -> {
                    try {
                        final Path base = root.resolve(baseName);
                        return HarnessCheckHelper.safeFileOrWalk(root, base).stream()
                                .filter(file -> !excludedPaths.stream().anyMatch(file::startsWith))
                                .filter(file -> extPattern.matcher(file.toString()).matches())
                                .flatMap(file -> checkLeaks(root, file, patternsNode, severity).stream());
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> checkLeaks(Path root, Path file, JsonNode patternsNode, String severity) throws MojoExecutionException {
        final String text = HarnessCheckHelper.readFile(root, file);
        return Stream.of(patternsNode.spliterator(), false)
                .filter(p -> Pattern.compile(p.get("pattern").asText()).matcher(text).find())
                .map(p -> new Finding(severity, CATEGORY, p.get("label").asText() + " in active asset: " + root.relativize(file)))
                .toList();
    }
}
