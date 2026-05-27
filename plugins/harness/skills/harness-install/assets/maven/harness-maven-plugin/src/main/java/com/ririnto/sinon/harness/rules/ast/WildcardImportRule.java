package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Rule that forbids wildcard imports.
 */
public enum WildcardImportRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "wildcardImport";

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
                    .flatMap(file -> validateWildcardImport(root, file, severity).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateWildcardImport(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.getImports().stream()
                    .filter(imp -> imp.isAsterisk())
                    .map(imp -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + imp.getBegin().map(p -> p.line).orElse(-1) + ": wildcard import forbidden"))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
