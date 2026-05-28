package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rule that forbids mutable collection instantiation.
 */
public enum MutableCollectionRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "mutableCollection";
    private static final List<String> DEFAULT_CONSTRUCTORS = List.of("ArrayList", "HashMap", "HashSet", "LinkedList", "LinkedHashMap", "LinkedHashSet", "TreeMap", "TreeSet");
    private static final List<String> DEFAULT_FQNS = List.of("java.util.ArrayList", "java.util.HashMap", "java.util.HashSet", "java.util.LinkedList", "java.util.LinkedHashMap", "java.util.LinkedHashSet", "java.util.TreeMap", "java.util.TreeSet");
    private static final List<String> DEFAULT_ACCUMULATION_METHODS = List.of("add", "addAll", "put", "putAll");
    private static final List<String> DEFAULT_ALLOWED_BUILDERS = List.of("builder", "streamBuilder");

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
                    .flatMap(file -> validateMutableCollection(root, file, severity, manifest).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateMutableCollection(Path root, Path file, String severity, JsonNode manifest) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            final MutableConfig config = config(manifest);
            return Stream.concat(
                    cu.findAll(ObjectCreationExpr.class).stream()
                            .filter(expr -> config.isForbiddenConstructor(expr.getType().asString()))
                            .map(expr -> finding(root, file, severity, expr, expr.getType().asString())),
                    cu.findAll(MethodCallExpr.class).stream()
                            .filter(expr -> config.accumulationMethods().contains(expr.getNameAsString()))
                            .filter(expr -> expr.getScope().flatMap(MutableCollectionRule::qualifiedExpressionName).map(scope -> !config.allowedBuilders().contains(scope)).orElse(true))
                            .map(expr -> finding(root, file, severity, expr, expr.getNameAsString())))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private static Finding finding(Path root, Path file, String severity, Node node, String name) {
        return Finding.of(severity, CATEGORY, root.relativize(file) + ":" + node.getBegin().map(p -> p.line).orElse(-1) + ": mutable collection " + name + "; use immutable factory");
    }

    private static MutableConfig config(JsonNode manifest) {
        return new MutableConfig(
                configured(manifest, "forbiddenConstructors", DEFAULT_CONSTRUCTORS),
                configured(manifest, "forbiddenFqns", DEFAULT_FQNS),
                configured(manifest, "accumulationMethods", DEFAULT_ACCUMULATION_METHODS),
                configured(manifest, "allowedBuilders", DEFAULT_ALLOWED_BUILDERS)
        );
    }

    private static Set<String> configured(JsonNode manifest, String key, List<String> defaults) {
        final JsonNode section = manifest.get(CATEGORY);
        if (section == null) {
            return Set.copyOf(defaults);
        }
        final JsonNode parameters = section.get("parameters");
        if (parameters == null) {
            return Set.copyOf(defaults);
        }
        final List<String> configured = HarnessCheckHelper.extractPaths(parameters.get(key));
        return Set.copyOf(configured.isEmpty() ? defaults : configured);
    }

    private static Optional<String> qualifiedExpressionName(Expression expression) {
        final List<String> parts = expressionParts(expression);
        return parts.isEmpty() ? Optional.empty() : Optional.of(String.join(".", parts));
    }

    private static List<String> expressionParts(Expression expression) {
        if (expression.isNameExpr()) {
            return List.of(expression.asNameExpr().getNameAsString());
        }
        if (expression.isFieldAccessExpr()) {
            final FieldAccessExpr fieldAccess = expression.asFieldAccessExpr();
            final List<String> parts = new ArrayList<>(expressionParts(fieldAccess.getScope()));
            parts.add(fieldAccess.getNameAsString());
            return parts;
        }
        return List.of();
    }

    private record MutableConfig(Set<String> constructors, Set<String> forbiddenFqns, Set<String> accumulationMethods, Set<String> allowedBuilders) {
        private boolean isForbiddenConstructor(String typeName) {
            return constructors.contains(typeName) || forbiddenFqns.contains(typeName);
        }
    }
}
