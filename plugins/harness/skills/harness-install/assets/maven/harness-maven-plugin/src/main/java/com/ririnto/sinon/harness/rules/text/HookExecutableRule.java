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

/**
 * Rule that requires hook files to be executable.
 */
public enum HookExecutableRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "hookExecutable";
    }
    private static final String CATEGORY = "hookExecutable";

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks")).stream()
                .filter(hook -> HarnessCheckHelper.isSafeRegularFile(root, root.resolve(hook)))
                .filter(hook -> !Files.isExecutable(root.resolve(hook)))
                .map(hook -> Finding.of(severity, CATEGORY, hook + " must be executable"))
                .toList();
    }
}
