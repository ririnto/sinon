package com.ririnto.sinon.harness.rules.text;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires executable scripts to use /usr/bin/env shebang.
 */
public enum EnvShebangUsageRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "envShebangUsage";
    }
    private static final String CATEGORY = "envShebangUsage";

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final String expectedPrefix = catNode.get("parameters").get("expectedPrefix").asText();
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

    private List<Finding> validateShebang(Path root, Path file, String expectedPrefix, String severity) throws MojoExecutionException {
        final String text = HarnessCheckHelper.readFile(root, file);
        return text.startsWith("#!") && !text.startsWith(expectedPrefix)
                ? List.of(Finding.of(severity, CATEGORY, "executable script should use /usr/bin/env shebang: " + root.relativize(file)))
                : List.of();
    }
}
