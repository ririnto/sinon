package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that forbids unstructured logging via System.out/System.err.
 */
public enum ForbidUnstructuredLoggingRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidUnstructuredLogging";

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
                    .flatMap(file -> validateUnstructuredLogging(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateUnstructuredLogging(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(MethodCallExpr.class, expr -> {
                String methodStr = expr.toString();
                if (methodStr.startsWith("System.out.println") || methodStr.startsWith("System.out.print") ||
                    methodStr.startsWith("System.err.println") || methodStr.startsWith("System.err.print")) {
                    int line = expr.getBegin().map(p -> p.line).orElse(-1);
                    findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": unstructured logging; use structured logger"));
                }
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
