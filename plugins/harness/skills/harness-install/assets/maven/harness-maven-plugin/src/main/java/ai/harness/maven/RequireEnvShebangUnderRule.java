package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Stream;

/**
 * Rule that requires executable scripts to use /usr/bin/env shebang.
 */
public class RequireEnvShebangUnderRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireEnvShebangUnder";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        JsonNode catNode = manifest.get(CATEGORY);
        List<String> directories = HarnessCheckHelper.extractPaths(catNode.get("parameters").get("directories"));
        String expectedPrefix = catNode.get("parameters").get("expectedPrefix").asText();
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return directories.stream()
                .flatMap(dir -> {
                    try {
                        Path dirPath = root.resolve(dir);
                        return HarnessCheckHelper.safeFileOrWalk(root, dirPath).stream()
                                .filter(Files::isExecutable)
                                .flatMap(file -> validateShebang(root, file, expectedPrefix, severity).stream());
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> validateShebang(Path root, Path file, String expectedPrefix, String severity) throws MojoExecutionException {
        String text = HarnessCheckHelper.readFile(root, file);
        return text.startsWith("#!") && !text.startsWith(expectedPrefix)
                ? List.of(new Finding(severity, CATEGORY, "executable script should use /usr/bin/env shebang: " + root.relativize(file)))
                : List.of();
    }
}
