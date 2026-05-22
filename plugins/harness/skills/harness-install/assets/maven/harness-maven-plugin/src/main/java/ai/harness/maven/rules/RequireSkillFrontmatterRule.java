package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that requires skill files to have proper frontmatter.
 */
public enum RequireSkillFrontmatterRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireSkillFrontmatter";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        Path directory = root.resolve(".claude/skills");
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        if (!HarnessCheckHelper.isSafeDirectory(root, directory)) {
            return List.of(new Finding(severity, CATEGORY, ".claude/skills must contain at least one SKILL.md"));
        }
        List<Path> skillFiles = HarnessCheckHelper.safeFileOrWalk(root, directory).stream()
                .filter(f -> f.getFileName().toString().equals("SKILL.md"))
                .toList();
        if (skillFiles.isEmpty()) {
            return List.of(new Finding(severity, CATEGORY, ".claude/skills must contain at least one SKILL.md"));
        }
        Pattern descPattern = Pattern.compile("(?m)^description:\\s*.+$");
        return skillFiles.stream()
                .flatMap(f -> validateSkillFile(root, f, descPattern, severity).stream())
                .toList();
    }

    private List<Finding> validateSkillFile(Path root, Path file, Pattern descPattern, String severity) throws MojoExecutionException {
        String text = HarnessCheckHelper.readFile(root, file);
        String relative = root.relativize(file).toString();
        return Stream.of(
                text.startsWith("---") ? Stream.empty() : Stream.of(new Finding(severity, CATEGORY, "skill missing frontmatter: " + relative)),
                descPattern.matcher(text).find() ? Stream.empty() : Stream.of(new Finding(severity, CATEGORY, "skill missing description: " + relative))
        ).flatMap(s -> s).toList();
    }
}
