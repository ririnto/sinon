package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.JavaToken;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that forbids blank lines in leaf function bodies.
 */
public enum ForbidBlankLineInLeafFunctionRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidBlankLineInLeafFunction";

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
                    .flatMap(file -> validateBlankLinesInLeafFunctions(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateBlankLinesInLeafFunctions(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(MethodDeclaration.class, method -> {
                if (isLeafMethod(method)) {
                    method.getBody().ifPresent(body -> {
                        var tokenRange = body.getTokenRange();
                        if (tokenRange.isPresent()) {
                            var tokens = tokenRange.get();
                            int lastNewlineCount = 0;
                            for (JavaToken token : tokens) {
                                String text = token.getText();
                                if (token.getKind() == JavaToken.Kind.NEWLINE) {
                                    lastNewlineCount++;
                                } else if (!text.trim().isEmpty() && !text.matches("\\s+")) {
                                    if (lastNewlineCount > 1) {
                                        int line = token.getRange().map(r -> r.begin.line).orElse(-1);
                                        if (line > 0) {
                                            findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + (line - 1) + ": blank line in leaf function"));
                                        }
                                    }
                                    lastNewlineCount = 0;
                                } else if (text.matches("\\s+") && text.contains("\n")) {
                                    lastNewlineCount += text.chars().filter(c -> c == '\n').count();
                                }
                            }
                        }
                    });
                }
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private boolean isLeafMethod(MethodDeclaration method) {
        return method.getBody()
                .map(body -> {
                    java.util.List<MethodDeclaration> nestedMethods = new java.util.ArrayList<>();
                    java.util.List<com.github.javaparser.ast.expr.LambdaExpr> lambdas = new java.util.ArrayList<>();
                    body.walk(MethodDeclaration.class, nestedMethods::add);
                    body.walk(com.github.javaparser.ast.expr.LambdaExpr.class, lambdas::add);
                    return nestedMethods.isEmpty() && lambdas.isEmpty();
                })
                .orElse(false);
    }
}
