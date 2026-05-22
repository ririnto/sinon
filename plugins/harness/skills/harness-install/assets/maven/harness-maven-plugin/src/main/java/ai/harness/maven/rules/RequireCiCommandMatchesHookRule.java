package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;

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
        JsonNode catNode = manifest.get(CATEGORY);
        String refHook = catNode.get("parameters").get("referenceHook").asText();
        Path refPath = root.resolve(refHook);
        if (!HarnessCheckHelper.isSafeRegularFile(root, refPath)) {
            return List.of();
        }
        String refText = HarnessCheckHelper.readFile(root, refPath);
        String expectedCmd = extractHookCommand(refText);
        if (expectedCmd.isEmpty()) {
            return List.of();
        }
        List<String> ciFiles = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("ciFiles"));
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final String cmd = expectedCmd;
        return ciFiles.stream()
                .flatMap(ciFile -> validateCiFile(root, ciFile, cmd, severity).stream())
                .toList();
    }

    private List<Finding> validateCiFile(Path root, String ciFile, String expectedCmd, String severity) throws MojoExecutionException {
        Path ciPath = root.resolve(ciFile);
        List<Finding> findings;
        if (Files.exists(ciPath, LinkOption.NOFOLLOW_LINKS) && HarnessCheckHelper.isSafeRegularFile(root, ciPath)) {
            String ciText = HarnessCheckHelper.readFile(root, ciPath);
            if (ciText.contains(expectedCmd)) {
                findings = List.of();
            } else {
                findings = List.of(new Finding(severity, CATEGORY, ciFile + ": CI command mismatch — expected " + expectedCmd));
            }
        } else {
            findings = List.of();
        }
        return findings;
    }

    private String extractHookCommand(String text) {
        String result = "";
        for (String line : text.split("\\R")) {
            if (line.startsWith("# Harness validation command: ")) {
                result = line.substring("# Harness validation command: ".length()).trim();
                break;
            }
        }
        return result;
    }
}
