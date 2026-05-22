package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;

/**
 * Rule that requires imports instead of fully qualified names.
 */
public enum RequireImportOverFqnRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireImportOverFqn";

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
                    .flatMap(file -> validateImportOverFqn(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
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
            return cu.findAll(FieldAccessExpr.class).stream()
                    .filter(expr -> {
                        final String scope = expr.getScope().toString();
                        return scope.contains(".") && scope.split("\\.").length >= 2;
                    })
                    .filter(expr -> !importedSimpleNames.contains(expr.getNameAsString()))
                    .map(expr -> new Finding(severity, CATEGORY, root.relativize(file) + ":" + expr.getBegin().map(p -> p.line).orElse(-1) + ": use import instead of FQN"))
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
