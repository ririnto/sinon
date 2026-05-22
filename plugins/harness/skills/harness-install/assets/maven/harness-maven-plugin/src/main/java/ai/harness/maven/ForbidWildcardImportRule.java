package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Rule that forbids wildcard imports.
 */
public class ForbidWildcardImportRule implements HarnessCheckRule {
    private static final String CATEGORY = "forbidWildcardImport";

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
                    .flatMap(file -> validateWildcardImport(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateWildcardImport(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            List<Finding> findings = new java.util.ArrayList<>();
            cu.getImports().forEach(imp -> {
                if (imp.isAsterisk()) {
                    int line = imp.getBegin().map(p -> p.line).orElse(-1);
                    findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": wildcard import forbidden"));
                }
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
