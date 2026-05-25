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
 * Rule that requires hook files to contain a generated marker.
 */
public enum HookGeneratedMarkerRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "hookGeneratedMarker";
    }
    private static final String CATEGORY = "hookGeneratedMarker";

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final String markerTemplate = catNode.get("parameters").get("markerTemplate").asText();
        final String placeholderForbidden = catNode.get("parameters").get("placeholderForbidden").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks")).stream()
                .flatMap(hook -> {
                    try {
                        return validateMarker(root, hook, markerTemplate, placeholderForbidden, severity).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> validateMarker(Path root, String hook, String markerTemplate, String placeholderForbidden, String severity) throws MojoExecutionException {
        final Path hookPath = root.resolve(hook);
        return Stream.of(hookPath)
                .filter(p -> HarnessCheckHelper.isSafeRegularFile(root, p))
                .flatMap(p -> {
                    try {
                        final String text = HarnessCheckHelper.readFile(root, p);
                        final String expectedMarker = markerTemplate.replace("{name}", p.getFileName().toString());
                        return Stream.<Stream<Finding>>of(
                                text.contains(expectedMarker) ? Stream.<Finding>empty() : Stream.of(Finding.of(severity, CATEGORY, hook + " must contain generated marker '" + expectedMarker + "'")),
                                text.contains(placeholderForbidden) ? Stream.of(Finding.of(severity, CATEGORY, hook + " still contains packaging placeholder text")) : Stream.<Finding>empty()
                        ).flatMap(s -> s);
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
