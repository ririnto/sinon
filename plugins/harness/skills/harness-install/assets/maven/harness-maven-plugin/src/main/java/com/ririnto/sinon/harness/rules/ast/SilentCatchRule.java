package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Rule that forbids silent catch blocks.
 */
public enum SilentCatchRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "silentCatch";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final String severity = HarnessCheckHelper.getSeverity(ctx.manifest().raw(), CATEGORY);
        try {
            final List<Path> sources = ctx.stackSources(CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateSilentCatch(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateSilentCatch(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(CatchClause.class).stream()
                    .filter(catchClause -> !usesCatchParameter(catchClause) && !hasThrow(catchClause) && !hasLoggingCall(catchClause))
                    .map(catchClause -> new Finding(severity, CATEGORY, "silent catch block", root.relativize(file).toString(), catchClause.getBegin().map(p -> p.line).orElse(1), catchClause.getBegin().map(p -> p.column).orElse(1), null, null, null))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private boolean usesCatchParameter(CatchClause catchClause) {
        final String catchParam = catchClause.getParameter().getNameAsString();
        return catchClause.getBody().findAll(NameExpr.class).stream()
                .anyMatch(expr -> expr.getNameAsString().equals(catchParam));
    }

    private boolean hasThrow(CatchClause catchClause) {
        return !catchClause.getBody().findAll(ThrowStmt.class).isEmpty();
    }

    private boolean hasLoggingCall(CatchClause catchClause) {
        return catchClause.getBody().findAll(MethodCallExpr.class).stream()
                .anyMatch(this::isLoggingCall);
    }

    private boolean isLoggingCall(MethodCallExpr expr) {
        return expr.getScope()
                .map(this::isLoggingTarget)
                .orElse(false);
    }

    private boolean isLoggingTarget(Expression expr) {
        if (expr.isNameExpr()) {
            final String name = expr.asNameExpr().getNameAsString();
            return "logger".equals(name) || "log".equals(name);
        }
        if (expr.isFieldAccessExpr()) {
            final FieldAccessExpr field = expr.asFieldAccessExpr();
            return "logger".equals(field.getNameAsString()) || "log".equals(field.getNameAsString()) || isLoggingTarget(field.getScope());
        }
        if (expr.isMethodCallExpr()) {
            final MethodCallExpr call = expr.asMethodCallExpr();
            return "getLog".equals(call.getNameAsString()) || call.getScope().map(this::isLoggingTarget).orElse(false);
        }
        return false;
    }
}
