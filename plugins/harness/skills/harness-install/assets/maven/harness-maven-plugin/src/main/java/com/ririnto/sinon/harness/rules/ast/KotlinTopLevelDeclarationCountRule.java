package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires Kotlin files to have exactly one top-level declaration.
 */
public enum KotlinTopLevelDeclarationCountRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "kotlinTopLevelDeclarationCount";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public boolean applies(RuleContext ctx) {
        if (!ctx.manifest().isEnabled(CATEGORY)) {
            return false;
        }
        final var catNode = ctx.manifest().raw().get(CATEGORY);
        if (catNode == null || !catNode.has("parameters")) {
            return false;
        }
        final var params = catNode.get("parameters");
        final var roots = params.get("sourceRoots");
        return roots != null && roots.isArray() && !roots.isEmpty();
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            return HarnessCheckHelper.stackSources(manifest, CATEGORY, "kotlin", root).stream()
                .filter(file -> file.getFileName().toString().endsWith(".kt"))
                .flatMap(file -> {
                    try {
                        return validateKotlinDeclarations(root, file, severity, catNode).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
        } catch (java.io.IOException e) {
            throw new MojoExecutionException("failed to collect Kotlin sources", e);
        }
    }

    private List<Finding> validateKotlinDeclarations(Path root, Path file, String severity, JsonNode catNode) throws MojoExecutionException {
        final String text = HarnessCheckHelper.readFile(root, file);
        final long count = Stream.of(text.split("\n"))
                .map(String::trim)
                .filter(trimmed -> trimmed.startsWith("class ") || trimmed.startsWith("fun ") || trimmed.startsWith("interface ")
                        || trimmed.startsWith("object ") || trimmed.startsWith("enum class "))
                .count();
        return Stream.of(count)
                .filter(c -> c != 1)
                .map(c -> Finding.of(severity, CATEGORY, "Kotlin file must have exactly 1 top-level declaration: " + root.relativize(file)))
                .toList();
    }
}
