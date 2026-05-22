package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that forbids mutable collection instantiation.
 */
public enum ForbidMutableCollectionRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidMutableCollection";

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
                    .flatMap(file -> validateMutableCollection(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateMutableCollection(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(ObjectCreationExpr.class).stream()
                    .filter(expr -> {
                        String typeName = expr.getTypeAsString();
                        return typeName.equals("ArrayList") || typeName.equals("HashMap") || typeName.equals("HashSet") ||
                                typeName.equals("LinkedList") || typeName.equals("LinkedHashMap") || typeName.equals("LinkedHashSet") ||
                                typeName.equals("TreeMap") || typeName.equals("TreeSet");
                    })
                    .map(expr -> {
                        int line = expr.getBegin().map(p -> p.line).orElse(-1);
                        String typeName = expr.getTypeAsString();
                        return new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": mutable collection " + typeName + "; use immutable factory");
                    })
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
