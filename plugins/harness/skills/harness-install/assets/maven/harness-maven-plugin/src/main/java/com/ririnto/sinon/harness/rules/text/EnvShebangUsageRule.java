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
 * Rule that requires executable scripts to use /usr/bin/env shebang.
 */
public enum EnvShebangUsageRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "envShebangUsage";

    @Override
    public String category() {
        return "envShebangUsage";
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
        final String expectedPrefix = catNode.get("parameters").get("expectedPrefix").asString();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("directories")).stream()
                .flatMap(dir -> {
                    final Path dirPath = root.resolve(dir);
                    try {
                        return HarnessCheckHelper.safeFileOrWalk(root, dirPath).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .filter(Files::isExecutable)
                .flatMap(file -> {
                    try {
                        return validateShebang(root, file, expectedPrefix, severity).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    /**
     * Replaces the shebang of executable scripts that do not use the /usr/bin/env form.
     * Extracts the interpreter from the current shebang, converts it to env form.
     * For example, #!/bin/bash becomes #!/usr/bin/env bash.
     */
    @Override
    public Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        if (catNode == null || catNode.get("parameters") == null || catNode.get("parameters").get("directories") == null) {
            return List.of();
        }
        final String expectedPrefix = catNode.get("parameters").get("expectedPrefix").asString();
        final List<Path> formatted = new ArrayList<>();
        for (final String dir : HarnessCheckHelper.extractPaths(catNode.get("parameters").get("directories"))) {
            final Path dirPath = root.resolve(dir);
            try {
                for (final Path file : HarnessCheckHelper.safeFileOrWalk(root, dirPath)) {
                    if (!Files.isExecutable(file)) {
                        continue;
                    }
                    final String text = Files.readString(file, StandardCharsets.UTF_8);
                    if (text.startsWith("#!") && !text.startsWith(expectedPrefix)) {
                        final int newlineIdx = text.indexOf('\n');
                        final String shebang = newlineIdx < 0 ? text : text.substring(0, newlineIdx);
                        final String rest = newlineIdx < 0 ? "" : text.substring(newlineIdx);
                        final String[] parts = shebang.substring(2).trim().split("\\s+");
                        final String interpreter = parts.length > 0 ? parts[parts.length - 1] : "sh";
                        final String newShebang = expectedPrefix.stripTrailing() + " " + interpreter;
                        Files.writeString(file, newShebang + rest, StandardCharsets.UTF_8);
                        formatted.add(file);
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to format shebang in directory " + dir, e);
            }
        }
        return formatted;
    }

    private List<Finding> validateShebang(Path root, Path file, String expectedPrefix, String severity) throws MojoExecutionException {
        final String text = HarnessCheckHelper.readFile(root, file);
        return text.startsWith("#!") && !text.startsWith(expectedPrefix)
                ? List.of(Finding.of(severity, CATEGORY, "executable script should use /usr/bin/env shebang: " + root.relativize(file)))
                : List.of();
    }
}
