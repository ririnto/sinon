package com.ririnto.sinon.harness.rules.text;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

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

    private static final String CATEGORY = "skillFrontmatter";

    @Override
    public String category() {
        return "skillFrontmatter";
    }

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final Path directory = root.resolve(".claude/skills");
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        if (!HarnessCheckHelper.isSafeDirectory(root, directory)) {
            return List.of(Finding.of(severity, CATEGORY, ".claude/skills must contain at least one SKILL.md"));
        }
        final List<Path> skillFiles = HarnessCheckHelper.safeFileOrWalk(root, directory).stream()
                .filter(f -> f.getFileName().toString().equals("SKILL.md"))
                .toList();
        if (skillFiles.isEmpty()) {
            return List.of(Finding.of(severity, CATEGORY, ".claude/skills must contain at least one SKILL.md"));
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
                text.startsWith("---") ? Stream.<Finding>empty() : Stream.of(Finding.of(severity, CATEGORY, "skill missing frontmatter: " + relative)),
                descPattern.matcher(text).find() ? Stream.<Finding>empty() : Stream.of(Finding.of(severity, CATEGORY, "skill missing description: " + relative))
        ).flatMap(s -> s).toList();
    }
}
