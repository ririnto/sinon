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

/**
 * Rule that forbids silent catch blocks.
 */
public enum ForbidSilentCatchRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidSilentCatch";

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
                    .flatMap(file -> validateSilentCatch(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateSilentCatch(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(CatchClause.class).stream()
                    .filter(catchClause -> {
                        String catchParam = catchClause.getParameter().getNameAsString();
                        String bodyText = catchClause.getBody().toString();
                        return !bodyText.contains(catchParam) && !bodyText.contains("throw ") && !bodyText.matches("(?s).*\\b(getLog|logger|log)\\s*\\..*");
                    })
                    .map(catchClause -> new Finding(severity, CATEGORY, root.relativize(file) + ":" + catchClause.getBegin().map(p -> p.line).orElse(-1) + ": silent catch block"))
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
