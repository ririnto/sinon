package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Rule that requires imports instead of fully qualified names.
 */
public class RequireImportOverFqnRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireImportOverFqn";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            List<Path> sources = HarnessCheckHelper.stackSources(manifest, CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateImportOverFqn(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateImportOverFqn(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            Set<String> importedSimpleNames = new java.util.HashSet<>();
            cu.getImports().forEach(imp -> {
                String name = imp.getNameAsString();
                if (!imp.isAsterisk()) {
                    int lastDot = name.lastIndexOf('.');
                    if (lastDot > 0) {
                        importedSimpleNames.add(name.substring(lastDot + 1));
                    }
                }
            });
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(FieldAccessExpr.class, expr -> {
                String scope = expr.getScope().toString();
                if (scope.contains(".") && scope.split("\\.").length >= 2) {
                    String simple = expr.getNameAsString();
                    if (!importedSimpleNames.contains(simple)) {
                        int line = expr.getBegin().map(p -> p.line).orElse(-1);
                        findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": use import instead of FQN"));
                    }
                }
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
