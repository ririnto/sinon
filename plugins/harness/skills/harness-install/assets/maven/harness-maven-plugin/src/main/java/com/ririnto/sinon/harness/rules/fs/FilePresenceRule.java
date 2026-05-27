package com.ririnto.sinon.harness.rules.fs;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that requires specified files to exist in the project.
 */
public enum FilePresenceRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "filePresence";

    @Override
    public String category() {
        return "filePresence";
    }

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(manifest.get(CATEGORY).get("parameters").get("paths")).stream()
                .filter(path -> !HarnessCheckHelper.isSafeRegularFile(root, root.resolve(path)))
                .map(path -> Finding.of(severity, CATEGORY, "missing file: " + path))
                .toList();
    }
}
