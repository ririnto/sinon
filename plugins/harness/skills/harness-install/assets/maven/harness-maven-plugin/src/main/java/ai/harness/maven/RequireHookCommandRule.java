package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that requires hook files to declare and run harness validation commands.
 */
public class RequireHookCommandRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireHookCommand";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        JsonNode allowedCmds = catNode.get("parameters").get("allowedCommands").get("maven");
        String expectedCmd = allowedCmds == null || allowedCmds.size() == 0 ? "" : allowedCmds.get(0).asText();
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        String prePushPath = catNode.get("parameters").get("prePushHook").asText();
        Path prePush = root.resolve(prePushPath);
        if (!HarnessCheckHelper.isSafeRegularFile(root, prePush)) {
            return List.of();
        }
        String prePushText = HarnessCheckHelper.readFile(root, prePush);
        String declaredCmd = extractHookCommand(prePushText);
        List<Finding> prePushIssues = declaredCmd.isEmpty()
                ? List.of(new Finding(severity, CATEGORY, "pre-push hook must declare Harness validation command"))
                : !expectedCmd.equals(declaredCmd)
                ? List.of(new Finding(severity, CATEGORY, "pre-push hook declares unsupported validation command: " + declaredCmd))
                : !prePushText.contains(declaredCmd)
                ? List.of(new Finding(severity, CATEGORY, "pre-push hook must run the declared validation command"))
                : List.of();
        String preCommitPath = catNode.get("parameters").get("preCommitHook").asText();
        Path preCommit = root.resolve(preCommitPath);
        if (!HarnessCheckHelper.isSafeRegularFile(root, preCommit)) {
            return prePushIssues;
        }
        String preCommitText = HarnessCheckHelper.readFile(root, preCommit);
        Pattern fullStackPattern = Pattern.compile("(^|\\s)(uv|bun|gradle|mvn)(\\s|$)|\\.\\./gradlew|harnessValidate|harness_validate\\.py|harness-validate\\.ts");
        List<Finding> preCommitIssues = fullStackPattern.matcher(preCommitText).find()
                ? List.of(new Finding(severity, CATEGORY, "pre-commit hook must not run full stack validation commands"))
                : List.of();
        return Stream.concat(prePushIssues.stream(), preCommitIssues.stream()).toList();
    }

    private String extractHookCommand(String text) {
        for (String line : text.split("\\R")) {
            if (line.startsWith("# Harness validation command: ")) {
                return line.substring("# Harness validation command: ".length()).trim();
            }
        }
        return "";
    }
}
