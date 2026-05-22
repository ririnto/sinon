package ai.harness.maven;

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
public class ForbidSilentCatchRule implements HarnessCheckRule {
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
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(CatchClause.class, catchClause -> {
                String catchParam = catchClause.getParameter().getNameAsString();
                var body = catchClause.getBody();
                String bodyText = body.toString();
                boolean hasParamRef = bodyText.contains(catchParam);
                boolean hasThrow = bodyText.contains("throw ");
                boolean hasLog = bodyText.matches("(?s).*\\b(getLog|logger|log)\\s*\\..*");
                if (!hasParamRef && !hasThrow && !hasLog) {
                    int line = catchClause.getBegin().map(p -> p.line).orElse(-1);
                    findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": silent catch block"));
                }
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
