package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.BlockStmt;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires braces on all if and else statements.
 */
public enum IfStatementBracesRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "ifStatementBraces";

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
                    .flatMap(file -> validateBracesOnIf(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateBracesOnIf(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(IfStmt.class).stream()
                    .flatMap(ifStmt -> {
                        final Stream.Builder<Finding> builder = Stream.builder();
                        if (!(ifStmt.getThenStmt() instanceof BlockStmt)) {
                            builder.add(Finding.of(severity, CATEGORY, root.relativize(file) + ":" + ifStmt.getBegin().map(p -> p.line).orElse(-1) + ": if without braces"));
                        }
                        ifStmt.getElseStmt().ifPresent(elseStmt -> {
                            if (!(elseStmt instanceof BlockStmt) && !(elseStmt instanceof IfStmt)) {
                                builder.add(Finding.of(severity, CATEGORY, root.relativize(file) + ":" + elseStmt.getBegin().map(p -> p.line).orElse(-1) + ": else without braces"));
                            }
                        });
                        return builder.build();
                    })
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
