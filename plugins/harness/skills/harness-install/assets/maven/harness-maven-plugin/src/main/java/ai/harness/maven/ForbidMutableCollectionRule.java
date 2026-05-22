package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Rule that forbids mutable collection instantiation.
 */
public class ForbidMutableCollectionRule implements HarnessCheckRule {
    private static final String CATEGORY = "forbidMutableCollection";

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
                    .flatMap(file -> validateMutableCollection(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateMutableCollection(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(ObjectCreationExpr.class, expr -> {
                String typeName = expr.getTypeAsString();
                if (typeName.equals("ArrayList") || typeName.equals("HashMap") || typeName.equals("HashSet") ||
                    typeName.equals("LinkedList") || typeName.equals("LinkedHashMap") || typeName.equals("LinkedHashSet") ||
                    typeName.equals("TreeMap") || typeName.equals("TreeSet")) {
                    int line = expr.getBegin().map(p -> p.line).orElse(-1);
                    findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": mutable collection " + typeName + "; use immutable factory"));
                }
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
