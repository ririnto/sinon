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
import java.util.concurrent.atomic.AtomicInteger;

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
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            final List<Path> sources = HarnessCheckHelper.stackSources(manifest, CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateBlankLinesInLeafFunctions(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateBlankLinesInLeafFunctions(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            return cu.findAll(MethodDeclaration.class).stream()
                    .filter(this::isLeafMethod)
                    .flatMap(method -> method.getBody()
                            .flatMap(body -> body.getTokenRange()
                                    .map(tokenRange -> collectBlankLineFindings(root, file, tokenRange, severity))
                                    .map(java.util.stream.Stream::of)
                                    .orElse(java.util.stream.Stream.empty()))
                            .orElse(java.util.stream.Stream.empty())
                            .stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private java.util.List<Finding> collectBlankLineFindings(Path root, Path file, com.github.javaparser.TokenRange tokens, String severity) {
        final java.util.List<Finding> findings = new java.util.ArrayList<>();
        final AtomicInteger lastNewlineCount = new AtomicInteger(0);
        tokens.forEach(token -> {
            final String text = token.getText();
            if (token.getKind() == JavaToken.Kind.NEWLINE) {
                lastNewlineCount.incrementAndGet();
            } else if (!text.trim().isEmpty() && !text.matches("\\s+")) {
                if (lastNewlineCount.get() > 1) {
                    token.getRange().map(r -> r.begin.line).ifPresent(line -> {
                        if (line > 0) {
                            findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + (line - 1) + ": blank line in leaf function"));
                        }
                    });
                }
                lastNewlineCount.set(0);
            } else if (text.matches("\\s+") && text.contains("\n")) {
                lastNewlineCount.addAndGet((int) text.chars().filter(c -> c == '\n').count());
            }
        });
        return findings;
    }

    private boolean isLeafMethod(MethodDeclaration method) {
        return method.getBody()
                .map(body -> body.findAll(MethodDeclaration.class).isEmpty() && body.findAll(com.github.javaparser.ast.expr.LambdaExpr.class).isEmpty())
                .orElse(false);
    }
}
