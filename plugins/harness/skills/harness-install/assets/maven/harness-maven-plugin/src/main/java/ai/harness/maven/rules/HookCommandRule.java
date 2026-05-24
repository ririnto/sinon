package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that requires hook files to declare and run harness validation commands.
 */
public enum HookCommandRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "hookCommand";
    }
    private static final String CATEGORY = "hookCommand";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final JsonNode allowedCmds = catNode.get("parameters").get("allowedCommands").get("maven");
        final String expectedCmd = allowedCmds == null || allowedCmds.size() == 0 ? "" : allowedCmds.get(0).asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final String prePushPath = catNode.get("parameters").get("prePushHook").asText();
        final Path prePush = root.resolve(prePushPath);
        final List<Finding> prePushIssues = Stream.of(prePush)
                .filter(p -> HarnessCheckHelper.isSafeRegularFile(root, p))
                .flatMap(p -> {
                    try {
                        final String prePushText = HarnessCheckHelper.readFile(root, p);
                        final String declaredCmd = extractHookCommand(prePushText);
                        return Stream.of(declaredCmd.isEmpty()
                                ? new Finding(severity, CATEGORY, "pre-push hook must declare Harness validation command")
                                : !expectedCmd.equals(declaredCmd)
                                ? new Finding(severity, CATEGORY, "pre-push hook declares unsupported validation command: " + declaredCmd)
                                : !prePushText.contains(declaredCmd)
                                ? new Finding(severity, CATEGORY, "pre-push hook must run the declared validation command")
                                : null);
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        final String preCommitPath = catNode.get("parameters").get("preCommitHook").asText();
        final Path preCommit = root.resolve(preCommitPath);
        final List<Finding> preCommitIssues = validatePreCommitHook(root, severity, preCommit);
        return Stream.concat(prePushIssues.stream(), preCommitIssues.stream()).toList();
    }

    private String extractHookCommand(String text) {
        return Stream.of(text.split("\\R"))
                .filter(line -> line.startsWith("# Harness validation command: "))
                .map(line -> line.substring("# Harness validation command: ".length()).trim())
                .findFirst()
                .orElse("");
    }

    private List<Finding> validatePreCommitHook(Path root, String severity, Path preCommit) throws MojoExecutionException {
        return Stream.of(preCommit)
                .filter(p -> HarnessCheckHelper.isSafeRegularFile(root, p))
                .flatMap(p -> {
                    try {
                        final String preCommitText = HarnessCheckHelper.readFile(root, p);
                        final Pattern fullStackPattern = Pattern.compile("(^|\\s)(uv|bun|gradle|mvn)(\\s|$)|\\.\\./gradlew|harnessValidate|harness_validate\\.py|harness-validate\\.ts");
                        return fullStackPattern.matcher(preCommitText).find()
                                ? Stream.of(new Finding(severity, CATEGORY, "pre-commit hook must not run full stack validation commands"))
                                : Stream.empty();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
