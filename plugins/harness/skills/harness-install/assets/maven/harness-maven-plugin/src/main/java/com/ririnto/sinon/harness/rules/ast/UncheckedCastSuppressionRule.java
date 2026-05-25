package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ArrayInitializerExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Rule that forbids @SuppressWarnings annotations with forbidden tokens.
 *
 * Detects all argument forms:
 * - @SuppressWarnings("TOKEN") — single positional
 * - @SuppressWarnings({"TOKEN", "OTHER"}) — array positional
 * - @SuppressWarnings(value = "TOKEN") — named single
 * - @SuppressWarnings(value = {"TOKEN", "OTHER"}) — named array
 *
 * Configurable via parameters.forbiddenSuppressions and parameters.allowedSuppressions.
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
        final Set<String> allowedTokens = resolveAllowedSuppressions(manifest);
        try {
            final List<Path> sources = ctx.stackSources(CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateFile(root, file, severity, forbiddenTokens, allowedTokens).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateFile(Path root, Path file, String severity, Set<String> forbiddenTokens, Set<String> allowedTokens) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(AnnotationExpr.class).stream()
                    .filter(ann -> isSuppressWarnings(ann))
                    .filter(ann -> hasForbiddenToken(ann, forbiddenTokens, allowedTokens))
                    .map(ann -> Finding.of(severity, CATEGORY, root.relativize(file) + ":" + ann.getBegin().map(p -> p.line).orElse(-1) + ": avoid suppression of forbidden tokens (`" + ann.toString() + "`); refactor to type-safe cast or explicit handling"))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private boolean isSuppressWarnings(AnnotationExpr ann) {
        return ann.getNameAsString().equals("SuppressWarnings");
    }

    private boolean hasForbiddenToken(AnnotationExpr ann, Set<String> forbiddenTokens, Set<String> allowedTokens) {
        final Set<String> tokens = extractSuppressTokens(ann);
        return !tokens.stream().filter(token -> forbiddenTokens.contains(token) && !allowedTokens.contains(token)).collect(Collectors.toSet()).isEmpty();
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
        if (ann.isSingleMemberAnnotationExpr()) {
            final SingleMemberAnnotationExpr singleAnn = ann.asSingleMemberAnnotationExpr();
            return extractFromExpression(singleAnn.getMemberValue());
        } else if (ann.isNormalAnnotationExpr()) {
            final NormalAnnotationExpr normalAnn = ann.asNormalAnnotationExpr();
            return normalAnn.getPairs().stream()
                    .filter(pair -> "value".equals(pair.getNameAsString()))
                    .map(pair -> extractFromExpression(pair.getValue()))
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }

    /**
     * Extracts string literals from an expression (string literal or array).
     *
     * @param expr The expression to extract from.
     * @return Set of extracted string values.
     */
    private Set<String> extractFromExpression(Expression expr) {
        if (expr.isStringLiteralExpr()) {
            final String value = expr.asStringLiteralExpr().asString();
            return value.isEmpty() ? Set.of() : Set.of(value);
        } else if (expr.isArrayInitializerExpr()) {
            return extractFromArrayInitializer(expr.asArrayInitializerExpr());
        } else if (expr.isArrayCreationExpr()) {
            return expr.asArrayCreationExpr().getInitializer()
                    .map(this::extractFromArrayInitializer)
                    .orElse(Set.of());
        }
        return Set.of();
    }

    /**
     * Extracts string literals from an array expression.
     *
     * @param expr The array expression.
     * @return Set of extracted string values.
     */
    private Set<String> extractFromArrayInitializer(ArrayInitializerExpr expr) {
        return expr.getValues().stream()
                    .filter(Expression::isStringLiteralExpr)
                    .map(Expression::asStringLiteralExpr)
                    .map(StringLiteralExpr::asString)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toSet());
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
        final Set<String> result = StreamSupport.stream(tokens.spliterator(), false)
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
        return result.isEmpty() ? Set.of("unchecked") : result;
    }

    /**
     * Resolves allowedSuppressions from manifest parameters.
     *
     * @param manifest Manifest JSON node.
     * @return Set of allowed suppression tokens.
     */
    private Set<String> resolveAllowedSuppressions(JsonNode manifest) {
        final JsonNode section = manifest.get(CATEGORY);
        if (section == null) {
            return Set.of();
        }
        final JsonNode params = section.get("parameters");
        if (params == null) {
            return Set.of();
        }
        final JsonNode tokens = params.get("allowedSuppressions");
        if (tokens == null || !tokens.isArray()) {
            return Set.of();
        }
        return StreamSupport.stream(tokens.spliterator(), false)
                .filter(JsonNode::isString)
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
    }
}
