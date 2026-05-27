package com.ririnto.sinon.harness.rules.text;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that requires agent files to have proper frontmatter.
 */
public enum AgentFrontmatterRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "agentFrontmatter";

    @Override
    public String category() {
        return "agentFrontmatter";
    }

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final Path directory = root.resolve(".claude/agents");
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        if (!HarnessCheckHelper.isSafeDirectory(root, directory)) {
            return List.of(Finding.of(severity, CATEGORY, ".claude/agents must contain at least one .md agent"));
        }
        final List<Path> files;
        try (final Stream<Path> stream = Files.list(directory)) {
            files = stream
                    .filter(f -> !Files.isSymbolicLink(f))
                    .filter(f -> f.getFileName().toString().endsWith(".md"))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
        if (files.isEmpty()) {
            return List.of(Finding.of(severity, CATEGORY, ".claude/agents must contain at least one .md agent"));
        }
        final Pattern namePattern = Pattern.compile("(?m)^name:\\s*[-a-z0-9]+\\s*$");
        final Pattern descPattern = Pattern.compile("(?m)^description:\\s*.+$");
        return files.stream()
                .flatMap(f -> {
                    try {
                        return validateAgentFile(root, f, namePattern, descPattern, severity).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> validateAgentFile(Path root, Path file, Pattern namePattern, Pattern descPattern, String severity) throws MojoExecutionException {
        final String text = HarnessCheckHelper.readFile(root, file);
        final String relative = root.relativize(file).toString();
        return Stream.<Stream<Finding>>of(
                text.startsWith("---") ? Stream.<Finding>empty() : Stream.of(Finding.of(severity, CATEGORY, "agent missing frontmatter: " + relative)),
                namePattern.matcher(text).find() ? Stream.<Finding>empty() : Stream.of(Finding.of(severity, CATEGORY, "agent missing name: " + relative)),
                descPattern.matcher(text).find() ? Stream.<Finding>empty() : Stream.of(Finding.of(severity, CATEGORY, "agent missing description: " + relative))
        ).flatMap(s -> s).toList();
    }
}
