package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.stmt.CatchClause;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Rule that forbids empty catch blocks.
 */
public enum EmptyCatchBlockRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "emptyCatchBlock";

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
                    .flatMap(file -> validateEmptyCatch(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateEmptyCatch(Path root, Path file, String severity) {
        try {
            return StaticJavaParser.parse(file).findAll(CatchClause.class).stream()
                    .filter(catchClause -> catchClause.getBody().getStatements().isEmpty())
                    .map(catchClause -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + catchClause.getBegin().map(p -> p.line).orElse(-1) + ": empty catch block"))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
