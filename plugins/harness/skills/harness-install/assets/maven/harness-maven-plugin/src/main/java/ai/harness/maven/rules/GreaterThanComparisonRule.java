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
import java.util.List;

/**
 * Rule that forbids greater-than comparisons in favor of less-than.
 */
public enum GreaterThanComparisonRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "greaterThanComparison";
    }
    private static final String CATEGORY = "greaterThanComparison";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            final List<Path> sources = HarnessCheckHelper.stackSources(manifest, CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateGreaterThanComparison(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateGreaterThanComparison(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            return cu.findAll(BinaryExpr.class).stream()
                    .filter(expr -> expr.getOperator() == BinaryExpr.Operator.GREATER || expr.getOperator() == BinaryExpr.Operator.GREATER_EQUALS)
                    .map(expr -> new Finding(severity, CATEGORY, root.relativize(file) + ":" + expr.getBegin().map(p -> p.line).orElse(-1) + ": forbidden `" + (expr.getOperator() == BinaryExpr.Operator.GREATER ? ">" : ">=") + "`; use `" + (expr.getOperator() == BinaryExpr.Operator.GREATER ? "<" : "<=") + "`"))
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
