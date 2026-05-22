package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that forbids greater-than comparisons in favor of less-than.
 */
public enum ForbidGreaterThanComparisonRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidGreaterThanComparison";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            List<Path> sources = HarnessCheckHelper.stackSources(manifest, CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateGreaterThanComparison(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateGreaterThanComparison(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(BinaryExpr.class, expr -> {
                if (expr.getOperator() == BinaryExpr.Operator.GREATER || expr.getOperator() == BinaryExpr.Operator.GREATER_EQUALS) {
                    int line = expr.getBegin().map(p -> p.line).orElse(-1);
                    String op = expr.getOperator() == BinaryExpr.Operator.GREATER ? ">" : ">=";
                    String replacement = expr.getOperator() == BinaryExpr.Operator.GREATER ? "<" : "<=";
                    findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": forbidden `" + op + "`; use `" + replacement + "`"));
                }
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
