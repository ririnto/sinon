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
 * Rule that requires skill files to have proper frontmatter.
 */
public enum SkillFrontmatterRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "skillFrontmatter";
    }
    private static final String CATEGORY = "skillFrontmatter";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final Path directory = root.resolve(".claude/skills");
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        if (!HarnessCheckHelper.isSafeDirectory(root, directory)) {
            return List.of(new Finding(severity, CATEGORY, ".claude/skills must contain at least one SKILL.md"));
        }
        final List<Path> skillFiles = HarnessCheckHelper.safeFileOrWalk(root, directory).stream()
                .filter(f -> f.getFileName().toString().equals("SKILL.md"))
                .toList();
        if (skillFiles.isEmpty()) {
            return List.of(new Finding(severity, CATEGORY, ".claude/skills must contain at least one SKILL.md"));
        }
        final Pattern descPattern = Pattern.compile("(?m)^description:\\s*.+$");
        return skillFiles.stream()
                .flatMap(f -> {
                    try {
                        return validateSkillFile(root, f, descPattern, severity).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> validateSkillFile(Path root, Path file, Pattern descPattern, String severity) throws MojoExecutionException {
        final String text = HarnessCheckHelper.readFile(root, file);
        final String relative = root.relativize(file).toString();
        return Stream.<Stream<Finding>>of(
                text.startsWith("---") ? Stream.<Finding>empty() : Stream.of(new Finding(severity, CATEGORY, "skill missing frontmatter: " + relative)),
                descPattern.matcher(text).find() ? Stream.<Finding>empty() : Stream.of(new Finding(severity, CATEGORY, "skill missing description: " + relative))
        ).flatMap(s -> s).toList();
    }
}
