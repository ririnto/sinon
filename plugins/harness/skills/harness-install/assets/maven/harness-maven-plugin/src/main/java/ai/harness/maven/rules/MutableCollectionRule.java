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
import java.util.List;

/**
 * Rule that forbids mutable collection instantiation.
 */
public enum MutableCollectionRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "mutableCollection";
    }
    private static final String CATEGORY = "mutableCollection";

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
                    .flatMap(file -> validateMutableCollection(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateMutableCollection(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(ObjectCreationExpr.class).stream()
                    .filter(expr -> {
                        final String typeName = expr.getType().getNameAsString();
                        return typeName.equals("ArrayList") || typeName.equals("HashMap") || typeName.equals("HashSet") ||
                                typeName.equals("LinkedList") || typeName.equals("LinkedHashMap") || typeName.equals("LinkedHashSet") ||
                                typeName.equals("TreeMap") || typeName.equals("TreeSet");
                    })
                    .map(expr -> {
                        final String typeName = expr.getType().getNameAsString();
                        return new Finding(severity, CATEGORY, root.relativize(file) + ":" + expr.getBegin().map(p -> p.line).orElse(-1) + ": mutable collection " + typeName + "; use immutable factory");
                    })
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
