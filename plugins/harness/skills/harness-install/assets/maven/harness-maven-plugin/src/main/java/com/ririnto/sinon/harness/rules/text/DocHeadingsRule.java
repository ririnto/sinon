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

/**
 * Rule that requires specific headings in documentation files.
 */
public enum DocHeadingsRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "docHeadings";

    @Override
    public String category() {
        return CATEGORY;
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
        final JsonNode parameters = catNode.get("parameters");
        final JsonNode sourceFilter = parameters.get("sourceFilter");
        final String prefix = sourceFilter.get("prefix").asText();
        final String suffix = sourceFilter.get("suffix").asText();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final List<String> headings = HarnessCheckHelper.extractPaths(parameters.get("headings"));
        return HarnessCheckHelper.extractPaths(manifest.get("filePresence").get("parameters").get("paths")).stream()
                .filter(f -> f.startsWith(prefix) && f.endsWith(suffix))
                .flatMap(f -> validateHeadings(root, f, headings, severity).stream())
                .toList();
    }

    private List<Finding> validateHeadings(Path root, String file, List<String> headings, String severity) {
        final Path filePath = root.resolve(file);
        if (!HarnessCheckHelper.isSafeRegularFile(root, filePath)) {
            return List.of();
        }
        final String text;
        try {
            text = HarnessCheckHelper.readFile(root, filePath);
        } catch (MojoExecutionException e) {
            return List.of();
        }
        return headings.stream()
                .filter(h -> !text.contains(h))
                .map(h -> Finding.of(severity, CATEGORY, "doc missing " + h + ": " + file))
                .toList();
    }
}
