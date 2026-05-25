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
import java.util.stream.Stream;

/**
 * Rule that requires hook files to start with a specific shebang.
 */
public enum HookShebangRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "hookShebang";
    }
    private static final String CATEGORY = "hookShebang";

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final String expected = catNode.get("parameters").get("expectedShebang").asText();
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

    private List<Finding> validateHookShebang(Path root, String hook, String expected, String severity) throws MojoExecutionException {
        final Path hookPath = root.resolve(hook);
        return HarnessCheckHelper.isSafeRegularFile(root, hookPath) && !HarnessCheckHelper.readFile(root, hookPath).startsWith(expected)
                ? List.of(Finding.of(severity, CATEGORY, hook + " must start with " + expected))
                : List.of();
    }
}
