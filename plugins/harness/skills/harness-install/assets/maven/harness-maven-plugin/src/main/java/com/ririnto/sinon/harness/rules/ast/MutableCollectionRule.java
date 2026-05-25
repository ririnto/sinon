package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Rule that forbids mutable collection instantiation.
 */
public enum MutableCollectionRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "mutableCollection";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            final List<Path> sources = ctx.stackSources(CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateMutableCollection(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateMutableCollection(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(ObjectCreationExpr.class).stream()
                    .filter(expr -> {
                        final String typeName = expr.getType().getNameAsString();
                        return typeName.equals("ArrayList") || typeName.equals("HashMap") || typeName.equals("HashSet") ||
                                typeName.equals("LinkedList") || typeName.equals("LinkedHashMap") || typeName.equals("LinkedHashSet") ||
                                typeName.equals("TreeMap") || typeName.equals("TreeSet");
                    })
                    .map(expr -> {
                        final String typeName = expr.getType().getNameAsString();
                        return Finding.of(severity, CATEGORY, root.relativize(file) + ":" + expr.getBegin().map(p -> p.line).orElse(-1) + ": mutable collection " + typeName + "; use immutable factory");
                    })
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
