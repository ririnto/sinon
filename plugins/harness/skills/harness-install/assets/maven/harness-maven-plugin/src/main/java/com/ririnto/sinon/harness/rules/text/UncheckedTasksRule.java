package com.ririnto.sinon.harness.rules.text;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that forbids unchecked tasks in completed plan documents.
 */
public enum UncheckedTasksRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "uncheckedTasks";

    @Override
    public String category() {
        return "uncheckedTasks";
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
        final String directory = catNode.get("parameters").get("directory").asText();
        final String uncheckedPattern = catNode.get("parameters").get("uncheckedTaskPattern").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final Path dirPath = root.resolve(directory);
        final Pattern pattern = Pattern.compile(uncheckedPattern);
        return Stream.of(dirPath)
                .filter(p -> HarnessCheckHelper.isSafeDirectory(root, p))
                .flatMap(p -> {
                    try {
                        return HarnessCheckHelper.safeFileOrWalk(root, p).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .filter(f -> f.getFileName().toString().endsWith(".md"))
                .filter(f -> !f.getFileName().toString().equals(".gitkeep"))
                .flatMap(file -> {
                    try {
                        final String text = HarnessCheckHelper.readFile(root, file);
                        return pattern.matcher(text).find()
                                ? Stream.of(Finding.of(severity, CATEGORY, "completed plan has unchecked tasks: " + root.relativize(file)))
                                : Stream.empty();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
