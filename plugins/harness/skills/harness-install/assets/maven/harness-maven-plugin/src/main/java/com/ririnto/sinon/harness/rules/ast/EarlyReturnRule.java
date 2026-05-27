package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
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
        final String severity = HarnessCheckHelper.getSeverity(ctx.manifest().raw(), CATEGORY);
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
            return StaticJavaParser.parse(file).findAll(MethodDeclaration.class).stream()
                    .flatMap(method -> method.getBody()
                            .map(body -> {
                                final List<ReturnStmt> returnStmts = body.getStatements().stream()
                                        .filter(stmt -> stmt instanceof ReturnStmt)
                                        .map(stmt -> (ReturnStmt) stmt)
                                        .toList();
                                return returnStmts.stream()
                                        .filter(ret -> !ret.equals(returnStmts.isEmpty() ? null : returnStmts.get(returnStmts.size() - 1)))
                                        .map(ret -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + ret.getBegin().map(p -> p.line).orElse(-1) + ": early return in function"));
                            })
                            .orElse(Stream.<Finding>empty()))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
