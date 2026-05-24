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
import java.util.List;

/**
 * Rule that forbids early return statements in functions.
 */
public enum EarlyReturnRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "earlyReturn";
    }
    private static final String CATEGORY = "earlyReturn";

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
                    .flatMap(file -> validateEarlyReturn(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateEarlyReturn(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(MethodDeclaration.class).stream()
                    .flatMap(method -> method.getBody()
                            .map(body -> {
                                final List<ReturnStmt> returnStmts = body.findAll(ReturnStmt.class);
                                if (returnStmts.isEmpty()) {
                                    return java.util.stream.Stream.<Finding>empty();
                                }
                                final ReturnStmt lastReturn = returnStmts.get(returnStmts.size() - 1);
                                return returnStmts.stream()
                                        .filter(ret -> !ret.equals(lastReturn))
                                        .map(ret -> new Finding(severity, CATEGORY, root.relativize(file) + ":" + ret.getBegin().map(p -> p.line).orElse(-1) + ": early return in function"));
                            })
                            .orElse(java.util.stream.Stream.<Finding>empty()))
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
