package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that requires agent files to have proper frontmatter.
 */
public enum RequireAgentFrontmatterRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireAgentFrontmatter";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        Path directory = root.resolve(".claude/agents");
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        if (!HarnessCheckHelper.isSafeDirectory(root, directory)) {
            return List.of(new Finding(severity, CATEGORY, ".claude/agents must contain at least one .md agent"));
        }
        try (Stream<Path> stream = Files.list(directory)) {
            List<Path> files = stream
                    .filter(f -> !Files.isSymbolicLink(f) && f.getFileName().toString().endsWith(".md"))
                    .toList();
            if (files.isEmpty()) {
                return List.of(new Finding(severity, CATEGORY, ".claude/agents must contain at least one .md agent"));
            }
            Pattern namePattern = Pattern.compile("(?m)^name:\\s*[-a-z0-9]+\\s*$");
            Pattern descPattern = Pattern.compile("(?m)^description:\\s*.+$");
            return files.stream()
                    .flatMap(f -> validateAgentFile(root, f, namePattern, descPattern, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<Finding> validateAgentFile(Path root, Path file, Pattern namePattern, Pattern descPattern, String severity) throws MojoExecutionException {
        String text = HarnessCheckHelper.readFile(root, file);
        String relative = root.relativize(file).toString();
        return Stream.of(
                text.startsWith("---") ? Stream.empty() : Stream.of(new Finding(severity, CATEGORY, "agent missing frontmatter: " + relative)),
                namePattern.matcher(text).find() ? Stream.empty() : Stream.of(new Finding(severity, CATEGORY, "agent missing name: " + relative)),
                descPattern.matcher(text).find() ? Stream.empty() : Stream.of(new Finding(severity, CATEGORY, "agent missing description: " + relative))
        ).flatMap(s -> s).toList();
    }
}
