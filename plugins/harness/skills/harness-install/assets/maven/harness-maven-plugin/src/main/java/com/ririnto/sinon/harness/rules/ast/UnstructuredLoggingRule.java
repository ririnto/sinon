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
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
            final Set<String> forbiddenLoggingApis = loggingApis(manifest, "forbiddenLoggingApis", Set.of("System.out.println", "System.out.print", "System.err.println", "System.err.print"));
            final Set<String> allowedLoggingApis = loggingApis(manifest, "allowedLoggingApis", Set.of());
            return sources.stream()
                    .flatMap(file -> validateUnstructuredLogging(root, file, severity, forbiddenLoggingApis, allowedLoggingApis).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateUnstructuredLogging(Path root, Path file, String severity, Set<String> forbiddenLoggingApis, Set<String> allowedLoggingApis) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(MethodCallExpr.class).stream()
                    .map(expr -> loggingApi(expr, forbiddenLoggingApis, allowedLoggingApis)
                            .map(api -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + expr.getBegin().map(p -> p.line).orElse(-1) + ": unstructured logging `" + api + "`; use structured logger")))
                    .flatMap(optional -> optional.stream())
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private Set<String> loggingApis(JsonNode manifest, String key, Set<String> defaults) {
        final JsonNode section = manifest.get(CATEGORY);
        final JsonNode parameters = section == null ? null : section.get("parameters");
        final JsonNode values = parameters == null ? null : parameters.get(key);
        final Set<String> configured = Set.copyOf(HarnessCheckHelper.extractPaths(values));
        return configured.isEmpty() ? defaults : configured;
    }

    private Optional<String> loggingApi(MethodCallExpr methodCall, Set<String> forbiddenLoggingApis, Set<String> allowedLoggingApis) {
        final List<String> callParts = methodCallParts(methodCall);
        return forbiddenLoggingApis.stream()
                .filter(api -> !allowedLoggingApis.contains(api))
                .filter(api -> callParts.equals(apiParts(api)))
                .findFirst();
    }

    private static List<String> methodCallParts(MethodCallExpr methodCall) {
        final List<String> parts = methodCall.getScope()
                .map(UnstructuredLoggingRule::expressionParts)
                .map(ArrayList::new)
                .orElseGet(ArrayList::new);
        parts.add(methodCall.getNameAsString());
        return parts;
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
        if (expression.isMethodCallExpr()) {
            return methodCallParts(expression.asMethodCallExpr());
        }
        return List.of();
    }

    private static List<String> apiParts(String api) {
        final List<String> parts = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < api.length(); index++) {
            if (api.charAt(index) == '.') {
                parts.add(api.substring(start, index));
                start = index + 1;
            }
        }
        parts.add(api.substring(start));
        return parts;
    }
}
