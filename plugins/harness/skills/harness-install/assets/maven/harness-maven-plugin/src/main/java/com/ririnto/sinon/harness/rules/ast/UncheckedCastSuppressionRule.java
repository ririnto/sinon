package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayAccessExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule that forbids @SuppressWarnings annotations with forbidden tokens.
 *
 * Detects all argument forms:
 * - @SuppressWarnings("TOKEN") — single positional
 * - @SuppressWarnings({"TOKEN", "OTHER"}) — array positional
 * - @SuppressWarnings(value = "TOKEN") — named single
 * - @SuppressWarnings(value = {"TOKEN", "OTHER"}) — named array
 *
 * Configurable via parameters.forbiddenSuppressions (defaults to ["unchecked"]).
 */
public enum UncheckedCastSuppressionRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "uncheckedCastSuppression";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final Set<String> forbiddenTokens = resolveForbiddenSuppressions(manifest);
        try {
            final List<Path> sources = ctx.stackSources(CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateFile(root, file, severity, forbiddenTokens).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateFile(Path root, Path file, String severity, Set<String> forbiddenTokens) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(AnnotationExpr.class).stream()
                    .filter(ann -> isSuppressWarnings(ann))
                    .filter(ann -> hasForbiddenToken(ann, forbiddenTokens))
                    .map(ann -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + ann.getBegin().map(p -> p.line).orElse(-1) + ": avoid suppression of forbidden tokens (`" + ann.toString() + "`); refactor to type-safe cast or explicit handling"))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private boolean isSuppressWarnings(AnnotationExpr ann) {
        return ann.getNameAsString().equals("SuppressWarnings");
    }

    private boolean hasForbiddenToken(AnnotationExpr ann, Set<String> forbiddenTokens) {
        final Set<String> tokens = extractSuppressTokens(ann);
        return !tokens.stream().filter(forbiddenTokens::contains).collect(Collectors.toSet()).isEmpty();
    }

    /**
     * Extracts all string literals from a @SuppressWarnings annotation.
     *
     * Handles:
     * - Single member: @SuppressWarnings("TOKEN") or @SuppressWarnings({"TOKEN"})
     * - Named: @SuppressWarnings(value = "TOKEN") or @SuppressWarnings(value = {...})
     *
     * @param ann The @SuppressWarnings annotation.
     * @return Set of extracted string values.
     */
    private Set<String> extractSuppressTokens(AnnotationExpr ann) {
        final Set<String> tokens = new HashSet<>();
        if (ann.isSingleMemberAnnotationExpr()) {
            final SingleMemberAnnotationExpr singleAnn = ann.asSingleMemberAnnotationExpr();
            extractFromExpression(singleAnn.getMemberValue(), tokens);
        } else if (ann.isNormalAnnotationExpr()) {
            final NormalAnnotationExpr normalAnn = ann.asNormalAnnotationExpr();
            normalAnn.getPairs().stream()
                    .filter(pair -> "value".equals(pair.getNameAsString()))
                    .forEach(pair -> extractFromExpression(pair.getValue(), tokens));
        }
        return tokens;
    }

    /**
     * Extracts string literals from an expression (string literal or array).
     *
     * @param expr The expression to extract from.
     * @param tokens Accumulator set for extracted string values.
     */
    private void extractFromExpression(Expression expr, Set<String> tokens) {
        if (expr.isStringLiteralExpr()) {
            final String value = expr.asStringLiteralExpr().asString();
            if (!value.isEmpty()) {
                tokens.add(value);
            }
        } else if (expr.isArrayAccessExpr() || expr.isArrayCreationExpr()) {
            extractFromArray(expr, tokens);
        }
    }

    /**
     * Extracts string literals from an array expression.
     *
     * @param expr The array expression.
     * @param tokens Accumulator set for extracted string values.
     */
    private void extractFromArray(Expression expr, Set<String> tokens) {
        final String text = expr.toString();
        if (text.startsWith("{") && text.endsWith("}")) {
            final String content = text.substring(1, text.length() - 1).trim();
            if (!content.isEmpty()) {
                for (String element : content.split(",")) {
                    final String trimmed = element.trim();
                    if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                        final String value = trimmed.substring(1, trimmed.length() - 1);
                        if (!value.isEmpty()) {
                            tokens.add(value);
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolves forbiddenSuppressions from manifest parameters.
     *
     * Reads parameters.forbiddenSuppressions from the manifest section,
     * defaulting to ["unchecked"] when missing.
     *
     * @param manifest Manifest JSON node.
     * @return Set of forbidden suppression tokens.
     */
    private Set<String> resolveForbiddenSuppressions(JsonNode manifest) {
        final JsonNode section = manifest.get(CATEGORY);
        if (section == null) {
            return Set.of("unchecked");
        }
        final JsonNode params = section.get("parameters");
        if (params == null) {
            return Set.of("unchecked");
        }
        final JsonNode tokens = params.get("forbiddenSuppressions");
        if (tokens == null || !tokens.isArray()) {
            return Set.of("unchecked");
        }
        final Set<String> result = new HashSet<>();
        tokens.forEach(token -> {
            if (token.isTextual()) {
                result.add(token.asText());
            }
        });
        return result.isEmpty() ? Set.of("unchecked") : result;
    }
}
