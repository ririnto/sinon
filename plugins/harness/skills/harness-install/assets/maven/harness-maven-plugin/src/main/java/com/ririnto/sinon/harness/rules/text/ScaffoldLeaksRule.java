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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Rule that forbids scaffold template patterns in active assets.
 */
public enum ScaffoldLeaksRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "scaffoldLeaks";
    }
    private static final String CATEGORY = "scaffoldLeaks";

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final JsonNode scope = catNode.get("parameters").get("scope");
        final List<String> excluded = HarnessCheckHelper.extractPaths(scope.get("excludedSubtrees"));
        final List<String> extensions = HarnessCheckHelper.extractPaths(scope.get("extensions"));
        final JsonNode patternsNode = catNode.get("parameters").get("patterns");
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final List<Path> excludedPaths = excluded.stream().map(root::resolve).toList();
        final String extRegex = extensions.isEmpty() ? ".*\\.(md|txt)$" : ".*\\.(" + String.join("|", extensions.stream().map(Pattern::quote).toList()) + ")$";
        final Pattern extPattern = Pattern.compile(extRegex);
        return HarnessCheckHelper.extractPaths(scope.get("bases")).stream()
                .filter(HarnessCheckHelper::isSafeRelativeRootEntry)
                .flatMap(baseName -> {
                    final Path base = root.resolve(baseName).normalize();
                    try {
                        return HarnessCheckHelper.safeFileOrWalk(root, base).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .filter(file -> !excludedPaths.stream().anyMatch(file::startsWith))
                .filter(file -> extPattern.matcher(file.toString()).matches())
                .flatMap(file -> {
                    try {
                        return checkLeaks(root, file, patternsNode, severity).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> checkLeaks(Path root, Path file, JsonNode patternsNode, String severity) throws MojoExecutionException {
        final String text = stripMarkdownCode(HarnessCheckHelper.readFile(root, file));
        return StreamSupport.stream(patternsNode.spliterator(), false)
                .filter(p -> Pattern.compile(p.get("pattern").asText()).matcher(text).find())
                .map(p -> Finding.of(severity, CATEGORY, p.get("label").asText() + " in active asset: " + root.relativize(file)))
                .toList();
    }

    /**
     * Removes Markdown code blocks and inline code spans before prose-level checks.
     */
    private String stripMarkdownCode(String text) {
        final StringBuilder stripped = new StringBuilder();
        boolean inFence = false;
        String fenceMarker = "";
        for (String line : text.split("\\R", -1)) {
            final java.util.regex.Matcher fenceMatch = Pattern.compile("^ {0,3}(`{3,}|~{3,})").matcher(line);
            if (fenceMatch.find()) {
                final String marker = fenceMatch.group(1).substring(0, 1);
                if (!inFence) {
                    inFence = true;
                    fenceMarker = marker;
                } else if (marker.equals(fenceMarker)) {
                    inFence = false;
                }
                stripped.append(System.lineSeparator());
                continue;
            }
            if (!inFence) {
                stripped.append(line.replaceAll("`+[^`\\n]*`+", ""));
            }
            stripped.append(System.lineSeparator());
        }
        return stripped.toString();
    }
}
