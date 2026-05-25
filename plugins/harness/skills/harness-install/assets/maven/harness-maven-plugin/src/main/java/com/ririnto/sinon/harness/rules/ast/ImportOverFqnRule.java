package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
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
                    .flatMap(file -> validateImportOverFqn(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateImportOverFqn(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            final Set<String> importedSimpleNames = cu.getImports().stream()
                    .filter(imp -> !imp.isAsterisk())
                    .map(imp -> {
                        final String name = imp.getNameAsString();
                        final int lastDot = name.lastIndexOf('.');
                        return lastDot > 0 ? name.substring(lastDot + 1) : null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            return Stream.concat(
                            cu.findAll(FieldAccessExpr.class).stream()
                                    .map(expr -> candidate(expr.toString(), expr.getNameAsString(), expr.getBegin().map(p -> p.line).orElse(-1))),
                            cu.findAll(MethodReferenceExpr.class).stream()
                                    .map(expr -> candidate(expr.getScope().toString(), simpleName(expr.getScope().toString()), expr.getBegin().map(p -> p.line).orElse(-1))))
                    .filter(candidate -> isPackageQualifiedName(candidate.qualifiedName()))
                    .filter(candidate -> !importedSimpleNames.contains(candidate.simpleName()))
                    .sorted(Comparator.comparingInt(FqnCandidate::line).thenComparing(FqnCandidate::qualifiedName))
                    .map(candidate -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + candidate.line() + ": fully qualified name `" + candidate.qualifiedName() + "` used inline; add an import and use the simple name"))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
    private static boolean isPackageQualifiedName(String qualifiedName) {
        final String[] parts = qualifiedName.split("\\.");
        return parts.length >= 3 && startsLowercase(parts[0]) && startsLowercase(parts[1]) && startsUppercase(parts[parts.length - 1]);
    }

    private static boolean startsLowercase(String value) {
        return !value.isEmpty() && Character.isLowerCase(value.charAt(0));
    }

    private static boolean startsUppercase(String value) {
        return !value.isEmpty() && Character.isUpperCase(value.charAt(0));
    }

    private static FqnCandidate candidate(String qualifiedName, String simpleName, int line) {
        return new FqnCandidate(qualifiedName, simpleName, line);
    }

    private static String simpleName(String qualifiedName) {
        final int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot > 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    private record FqnCandidate(String qualifiedName, String simpleName, int line) {
    }
}
