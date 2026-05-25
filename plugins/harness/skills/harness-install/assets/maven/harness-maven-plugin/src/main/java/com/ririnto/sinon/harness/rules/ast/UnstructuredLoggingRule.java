package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Rule that forbids unstructured logging via System.out/System.err.
 */
public enum UnstructuredLoggingRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "unstructuredLogging";

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
                    .flatMap(file -> validateUnstructuredLogging(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateUnstructuredLogging(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(MethodCallExpr.class).stream()
                    .filter(expr -> {
                        final String methodStr = expr.toString();
                        return methodStr.startsWith("System.out.println") || methodStr.startsWith("System.out.print") ||
                                methodStr.startsWith("System.err.println") || methodStr.startsWith("System.err.print");
                    })
                    .map(expr -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + expr.getBegin().map(p -> p.line).orElse(-1) + ": unstructured logging; use structured logger"))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
