package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

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
import java.util.stream.Stream;

/**
 * Rule that forbids early return statements in functions.
 */
public enum EarlyReturnRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "earlyReturn";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            final List<Path> sources = ctx.stackSources(CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateEarlyReturn(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
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
                                    return Stream.<Finding>empty();
                                }
                                final ReturnStmt lastReturn = returnStmts.get(returnStmts.size() - 1);
                                return returnStmts.stream()
                                        .filter(ret -> !ret.equals(lastReturn))
                                        .map(ret -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + ret.getBegin().map(p -> p.line).orElse(-1) + ": early return in function"));
                            })
                            .orElse(Stream.<Finding>empty()))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
