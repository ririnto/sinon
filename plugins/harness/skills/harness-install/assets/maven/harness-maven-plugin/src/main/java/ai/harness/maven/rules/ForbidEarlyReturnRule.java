package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that forbids early return statements in functions.
 */
public enum ForbidEarlyReturnRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidEarlyReturn";

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
                    .flatMap(file -> validateEarlyReturn(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateEarlyReturn(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(MethodDeclaration.class, method -> {
                method.getBody().ifPresent(body -> {
                    List<ReturnStmt> returnStmts = new java.util.ArrayList<>();
                    body.walk(ReturnStmt.class, returnStmts::add);
                    if (!returnStmts.isEmpty()) {
                        ReturnStmt lastReturn = returnStmts.get(returnStmts.size() - 1);
                        List<ReturnStmt> nonLastReturns = returnStmts.stream()
                                .filter(ret -> !ret.equals(lastReturn))
                                .toList();
                        for (ReturnStmt ret : nonLastReturns) {
                            int line = ret.getBegin().map(p -> p.line).orElse(-1);
                            findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": early return in function"));
                        }
                    }
                });
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
