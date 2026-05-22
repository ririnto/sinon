package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires CI configuration to match hook commands.
 */
public enum RequireCiCommandMatchesHookRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireCiCommandMatchesHook";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final String refHook = catNode.get("parameters").get("referenceHook").asText();
        final Path refPath = root.resolve(refHook);
        if (!HarnessCheckHelper.isSafeRegularFile(root, refPath)) {
            return List.of();
        }
        final String refText = HarnessCheckHelper.readFile(root, refPath);
        final String expectedCmd = extractHookCommand(refText);
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return Stream.of(expectedCmd)
                .filter(cmd -> !cmd.isEmpty())
                .flatMap(cmd -> HarnessCheckHelper.extractPaths(catNode.get("parameters").get("ciFiles")).stream()
                        .flatMap(ciFile -> validateCiFile(root, ciFile, cmd, severity).stream()))
                .toList();
    }

    private List<Finding> validateCiFile(Path root, String ciFile, String expectedCmd, String severity) throws MojoExecutionException {
        final Path ciPath = root.resolve(ciFile);
        return Stream.of(ciPath)
                .filter(p -> Files.exists(p, LinkOption.NOFOLLOW_LINKS) && HarnessCheckHelper.isSafeRegularFile(root, p))
                .flatMap(p -> {
                    try {
                        final String ciText = HarnessCheckHelper.readFile(root, p);
                        return ciText.contains(expectedCmd)
                                ? Stream.empty()
                                : Stream.of(new Finding(severity, CATEGORY, ciFile + ": CI command mismatch — expected " + expectedCmd));
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private String extractHookCommand(String text) {
        return Stream.of(text.split("\\R"))
                .filter(line -> line.startsWith("# Harness validation command: "))
                .map(line -> line.substring("# Harness validation command: ".length()).trim())
                .findFirst()
                .orElse("");
    }
}
