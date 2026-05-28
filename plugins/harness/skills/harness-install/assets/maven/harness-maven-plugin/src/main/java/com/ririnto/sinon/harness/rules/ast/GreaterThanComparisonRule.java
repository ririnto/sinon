package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Rule that forbids greater-than comparisons in favor of
 * less-than.
 */
public enum GreaterThanComparisonRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "greaterThanComparison";

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
                    .flatMap(file -> validateGreaterThanComparison(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    /**
     * Rewrites side-effect-free greater-than comparisons to less-than form.
     */
    @Override
    public Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        final List<Path> changed = new ArrayList<>();
        try {
            for (final Path file : ctx.stackSources(CATEGORY)) {
                if (formatGreaterThanComparisons(file)) {
                    changed.add(file);
                }
            }
        } catch (MojoExecutionException e) {
            throw new MojoExecutionException("failed to enumerate sources: " + e.getMessage(), e);
        }
        return changed;
    }

    private boolean formatGreaterThanComparisons(Path file) throws MojoExecutionException {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            boolean changed = false;
            for (final BinaryExpr expr : cu.findAll(BinaryExpr.class)) {
                final BinaryExpr.Operator operator = expr.getOperator();
                if ((operator == BinaryExpr.Operator.GREATER || operator == BinaryExpr.Operator.GREATER_EQUALS)
                        && isSafeOperand(expr.getLeft())
                        && isSafeOperand(expr.getRight())) {
                    final Expression left = expr.getLeft().clone();
                    final Expression right = expr.getRight().clone();
                    expr.setLeft(right);
                    expr.setRight(left);
                    expr.setOperator(operator == BinaryExpr.Operator.GREATER ? BinaryExpr.Operator.LESS : BinaryExpr.Operator.LESS_EQUALS);
                    changed = true;
                }
            }
            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
            return changed;
        } catch (IOException e) {
            throw new MojoExecutionException("failed to format " + file + ": " + e.getMessage(), e);
        }
    }

    private boolean isSafeOperand(Expression expression) {
        return expression.isNameExpr() || expression.isLiteralExpr();
    }

    private List<Finding> validateGreaterThanComparison(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            return cu.findAll(BinaryExpr.class).stream()
                    .filter(expr -> expr.getOperator() == BinaryExpr.Operator.GREATER || expr.getOperator() == BinaryExpr.Operator.GREATER_EQUALS)
                    .map(expr -> new Finding(
                            severity,
                            CATEGORY,
                            "forbidden `" + (expr.getOperator() == BinaryExpr.Operator.GREATER ? ">" : ">=") + "`; use `" + (expr.getOperator() == BinaryExpr.Operator.GREATER ? "<" : "<=") + "`",
                            root.relativize(file).toString(),
                            expr.getBegin().map(p -> p.line).orElse(1),
                            expr.getBegin().map(p -> p.column).orElse(1),
                            expr.getEnd().map(p -> p.line).orElse(null),
                            expr.getEnd().map(p -> p.column).orElse(null),
                            null))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
