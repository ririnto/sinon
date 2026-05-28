package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Rule that requires imports instead of fully qualified names.
 */
public enum ImportOverFqnRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "importOverFqn";

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
                    .flatMap(file -> validateImportOverFqn(root, file, manifest, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateImportOverFqn(Path root, Path file, JsonNode manifest, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            final Set<String> importedSimpleNames = cu.getImports().stream()
                    .filter(imp -> !imp.isAsterisk())
                    .map(imp -> imp.getName().getIdentifier())
                    .collect(Collectors.toSet());
            return Stream.concat(
                            cu.findAll(FieldAccessExpr.class).stream()
                                    .map(expr -> candidate(expressionParts(expr), expr.getBegin().map(p -> p.line).orElse(-1))),
                            cu.findAll(MethodReferenceExpr.class).stream()
                                    .map(expr -> candidate(expressionParts(expr.getScope()), expr.getBegin().map(p -> p.line).orElse(-1))))
                    .flatMap(optional -> optional.stream())
                    .filter(candidate -> isPackageQualifiedName(candidate.nameParts()))
                    .filter(candidate -> !importedSimpleNames.contains(candidate.simpleName()))
                    .filter(candidate -> allowedFqnPatterns(manifest).stream().noneMatch(pattern -> pattern.matcher(candidate.qualifiedName()).matches()))
                    .sorted(Comparator.comparingInt(FqnCandidate::line).thenComparing(FqnCandidate::qualifiedName))
                    .map(candidate -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + candidate.line() + ": fully qualified name `" + candidate.qualifiedName() + "` used inline; add an import and use the simple name"))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private static boolean isPackageQualifiedName(List<String> parts) {
        return 3 <= parts.size() && startsLowercase(parts.get(0)) && startsLowercase(parts.get(1)) && startsUppercase(parts.get(parts.size() - 1));
    }

    private static boolean startsLowercase(String value) {
        return !value.isEmpty() && Character.isLowerCase(value.charAt(0));
    }

    private static boolean startsUppercase(String value) {
        return !value.isEmpty() && Character.isUpperCase(value.charAt(0));
    }

    private static Optional<FqnCandidate> candidate(List<String> nameParts, int line) {
        return nameParts.isEmpty() ? Optional.empty() : Optional.of(new FqnCandidate(nameParts, line));
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
        if (expression.isTypeExpr()) {
            return typeParts(expression.asTypeExpr());
        }
        return List.of();
    }

    private static List<String> typeParts(TypeExpr expression) {
        if (expression.getType().isClassOrInterfaceType()) {
            return typeParts(expression.getType().asClassOrInterfaceType());
        }
        return List.of();
    }

    private static List<String> typeParts(ClassOrInterfaceType type) {
        final List<String> parts = type.getScope()
                .map(ImportOverFqnRule::typeParts)
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);
        parts.add(type.getNameAsString());
        return parts;
    }

    private List<Pattern> allowedFqnPatterns(JsonNode manifest) {
        final JsonNode section = manifest.get(CATEGORY);
        final JsonNode parameters = section == null ? null : section.get("parameters");
        final JsonNode patterns = parameters == null ? null : parameters.get("allowedFqnPatterns");
        return HarnessCheckHelper.extractPaths(patterns).stream()
                .map(Pattern::compile)
                .toList();
    }

    private record FqnCandidate(List<String> nameParts, int line) {
        private String qualifiedName() {
            return String.join(".", nameParts);
        }

        private String simpleName() {
            return nameParts.get(nameParts.size() - 1);
        }
    }
}
