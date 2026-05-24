package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.CatchClause;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Rule that forbids empty catch blocks.
 */
public enum EmptyCatchBlockRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "emptyCatchBlock";
    }
    private static final String CATEGORY = "emptyCatchBlock";

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
                    .flatMap(file -> validateEmptyCatch(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateEmptyCatch(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(CatchClause.class).stream()
                    .filter(catchClause -> catchClause.getBody().getStatements().isEmpty())
                    .map(catchClause -> new Finding(severity, CATEGORY, root.relativize(file) + ":" + catchClause.getBegin().map(p -> p.line).orElse(-1) + ": empty catch block"))
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
