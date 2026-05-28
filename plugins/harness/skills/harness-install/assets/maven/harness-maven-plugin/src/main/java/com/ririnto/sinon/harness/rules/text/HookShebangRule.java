package com.ririnto.sinon.harness.rules.text;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires hook files to start with a specific shebang.
 */
public enum HookShebangRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "hookShebang";

    @Override
    public String category() {
        return "hookShebang";
    }

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final String expected = catNode.get("parameters").get("expectedShebang").asString();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks")).stream()
                .flatMap(hook -> {
                    try {
                        return validateHookShebang(root, hook, expected, severity).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    /**
     * Inserts the expected shebang as the first line of hook files that do not already start with it.
     * Existing file content is preserved below the inserted shebang.
     */
    @Override
    public Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        if (catNode == null || catNode.get("parameters") == null || catNode.get("parameters").get("hooks") == null) {
            return List.of();
        }
        final String expected = catNode.get("parameters").get("expectedShebang").asString();
        final List<Path> formatted = new ArrayList<>();
        for (final String hook : HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks"))) {
            final Path hookPath = root.resolve(hook);
            if (!HarnessCheckHelper.isSafeRegularFile(root, hookPath)) {
                continue;
            }
            try {
                final String existingText = Files.readString(hookPath, StandardCharsets.UTF_8);
                if (!existingText.startsWith(expected)) {
                    final String newContent = expected + "\n" + existingText;
                    Files.writeString(hookPath, newContent, StandardCharsets.UTF_8);
                    formatted.add(hookPath);
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to insert shebang in " + hook, e);
            }
        }
        return formatted;
    }

    private List<Finding> validateHookShebang(Path root, String hook, String expected, String severity) throws MojoExecutionException {
        final Path hookPath = root.resolve(hook);
        return HarnessCheckHelper.isSafeRegularFile(root, hookPath) && !HarnessCheckHelper.readFile(root, hookPath).startsWith(expected)
                ? List.of(Finding.of(severity, CATEGORY, hook + " must start with " + expected))
                : List.of();
    }
}
