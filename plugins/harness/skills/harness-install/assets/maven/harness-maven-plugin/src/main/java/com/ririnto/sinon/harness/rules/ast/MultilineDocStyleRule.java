package com.ririnto.sinon.harness.rules.ast;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.comments.JavadocComment;
import com.ririnto.sinon.harness.Finding;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import org.apache.maven.plugin.MojoExecutionException;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Rule that requires JavaDoc comments to use multiline block style.
 */
public enum MultilineDocStyleRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "multilineDocStyle";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        if (!"multiline".equals(docStyleMode(ctx.manifest().raw()))) {
            return List.of();
        }
        final String severity = HarnessCheckHelper.getSeverity(ctx.manifest().raw(), CATEGORY);
        return ctx.stackSources(CATEGORY).stream()
                .flatMap(file -> validateFile(ctx.root(), file, severity).stream())
                .toList();
    }

    private List<Finding> validateFile(Path root, Path file, String severity) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            return compilationUnit.getAllContainedComments().stream()
                    .filter(comment -> comment instanceof JavadocComment)
                    .filter(comment -> !comment.toString().contains("\n"))
                    .map(comment -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + comment.getBegin().map(position -> position.line).orElse(1) + ": JavaDoc comment must use multiline style"))
                    .toList();
        } catch (IOException error) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + error.getMessage()));
        }
    }

    private static String docStyleMode(JsonNode manifest) {
        final JsonNode section = manifest.get(CATEGORY);
        if (section == null || !section.has("parameters")) {
            return "multiline";
        }
        final JsonNode mode = section.get("parameters").get("docStyleMode");
        return mode == null ? "multiline" : mode.asText("multiline");
    }
}
