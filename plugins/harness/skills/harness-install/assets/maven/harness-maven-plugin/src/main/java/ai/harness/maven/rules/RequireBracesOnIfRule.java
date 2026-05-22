package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that requires braces on all if and else statements.
 */
public enum RequireBracesOnIfRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireBracesOnIf";

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
                    .flatMap(file -> validateBracesOnIf(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateBracesOnIf(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(IfStmt.class, ifStmt -> {
                if (!(ifStmt.getThenStmt() instanceof BlockStmt)) {
                    int line = ifStmt.getBegin().map(p -> p.line).orElse(-1);
                    findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": if without braces"));
                }
                if (ifStmt.getElseStmt().isPresent()) {
                    com.github.javaparser.ast.stmt.Statement elseStmt = ifStmt.getElseStmt().get();
                    if (!(elseStmt instanceof BlockStmt) && !(elseStmt instanceof IfStmt)) {
                        int line = elseStmt.getBegin().map(p -> p.line).orElse(-1);
                        findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": else without braces"));
                    }
                }
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
