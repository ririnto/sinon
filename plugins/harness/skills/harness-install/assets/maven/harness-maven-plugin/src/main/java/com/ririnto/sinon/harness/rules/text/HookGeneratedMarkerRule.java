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
 * Rule that requires hook files to contain a generated marker.
 */
public enum HookGeneratedMarkerRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "hookGeneratedMarker";

    @Override
    public String category() {
        return "hookGeneratedMarker";
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

    /**
     * Automatically inserts generated marker at the beginning of hook files.
     * Only handles missing marker (SAFE). Placeholder presence (MANUAL) is skipped.
     *
     * @param ctx The rule context containing root path and manifest.
     * @return Collection of paths that were modified.
     * @throws MojoExecutionException if file operations fail.
     */
    @Override
    public Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        if (catNode == null) {
            return List.of();
        }
        final JsonNode parametersNode = catNode.get("parameters");
        if (parametersNode == null) {
            return List.of();
        }
        final JsonNode hooksNode = parametersNode.get("hooks");
        if (hooksNode == null) {
            return List.of();
        }
        final String markerTemplate = parametersNode.get("markerTemplate").asText();
        final List<Path> formatted = new ArrayList<>();
        for (final String hook : HarnessCheckHelper.extractPaths(hooksNode)) {
            final Path hookPath = root.resolve(hook);
            if (!HarnessCheckHelper.isSafeRegularFile(root, hookPath)) {
                continue;
            }
            try {
                final String text = Files.readString(hookPath, StandardCharsets.UTF_8);
                final String expectedMarker = markerTemplate.replace("{name}", hookPath.getFileName().toString());
                if (!text.contains(expectedMarker)) {
                    Files.writeString(hookPath, expectedMarker + "\n" + text, StandardCharsets.UTF_8);
                    formatted.add(hookPath);
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to insert marker in " + hook, e);
            }
        }
        return formatted;
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
