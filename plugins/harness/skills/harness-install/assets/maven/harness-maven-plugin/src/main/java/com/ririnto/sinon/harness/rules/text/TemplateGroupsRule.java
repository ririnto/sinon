package com.ririnto.sinon.harness.rules.text;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that requires specific template groups to exist.
 */
public enum TemplateGroupsRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "templateGroups";

    @Override
    public String category() {
        return "templateGroups";
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
        final String targetRoot = catNode.get("parameters").get("targetRoot").asString();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("groups")).stream()
                .filter(g -> !HarnessCheckHelper.isSafeDirectory(root, root.resolve(targetRoot).resolve(g)))
                .map(g -> Finding.of(severity, CATEGORY, "missing template group: " + targetRoot + "/" + g))
                .toList();
    }
}
