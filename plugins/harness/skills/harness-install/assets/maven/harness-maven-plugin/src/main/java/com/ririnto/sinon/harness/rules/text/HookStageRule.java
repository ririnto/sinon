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
 * Rule that requires hook files to contain stage markers.
 */
public enum HookStageRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "hookStage";
    }
    private static final String CATEGORY = "hookStage";

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final JsonNode stages = catNode.get("parameters").get("stages");
        final JsonNode stackStages = stages.get("maven");
        final String markerTemplate = catNode.get("parameters").get("markerTemplate").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return Stream.ofNullable(stackStages)
                .flatMap(ss -> ss.propertyNames().stream()
                        .flatMap(hookName -> validateStage(root, hookName, ss.get(hookName).asText(), markerTemplate, severity).stream()))
                .toList();
    }

    private List<Finding> validateStage(Path root, String hookName, String expectedStage, String markerTemplate, String severity) {
        final String hookPath = "docs/harness/git-hooks/" + hookName;
        final Path hook = root.resolve(hookPath);
        return Stream.of(hook)
                .filter(p -> HarnessCheckHelper.isSafeRegularFile(root, p))
                .flatMap(p -> {
                    try {
                        final String text = HarnessCheckHelper.readFile(root, p);
                        final String expectedMarker = markerTemplate.replace("{stage}", expectedStage);
                        return text.contains(expectedMarker)
                                ? Stream.empty()
                                : Stream.of(Finding.of(severity, CATEGORY, hookPath + " must contain stage marker '" + expectedMarker + "'"));
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
