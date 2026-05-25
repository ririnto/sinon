package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rule that requires documentation comments on declarations matching configured visibility levels.
 */
public enum PublicDeclarationDocCommentRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "publicDeclarationDocComment";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final Set<String> visibilityTokens = configuredVisibilityTokens(manifest);
        if (visibilityTokens.isEmpty()) {
            return List.of();
        }
        try {
            final List<Path> sources = ctx.stackSources(CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateDocComments(root, file, severity, visibilityTokens).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    /**
     * Extracts the configured visibility tokens from the manifest.
     *
     * @param manifest the manifest JSON node
     * @return immutable set of visibility tokens; defaults to ["public"] if not configured
     */
    private static Set<String> configuredVisibilityTokens(JsonNode manifest) {
        final JsonNode catNode = manifest.get(CATEGORY);
        if (catNode == null) {
            return Set.of("public");
        }
        final JsonNode params = catNode.get("parameters");
        if (params == null) {
            return Set.of("public");
        }
        final JsonNode visibility = params.get("visibility");
        if (visibility == null || !visibility.isArray()) {
            return Set.of("public");
        }
        final List<String> tokens = HarnessCheckHelper.extractPaths(visibility);
        return tokens.isEmpty() ? Set.of() : Set.copyOf(tokens);
    }

    /**
     * Returns the effective visibility level of a class or interface declaration.
     *
     * @param decl the class or interface declaration
     * @return one of "public", "protected", "package", or "private"
     */
    private static String effectiveVisibility(ClassOrInterfaceDeclaration decl) {
        if (decl.isPublic()) {
            return "public";
        }
        if (decl.isProtected()) {
            return "protected";
        }
        if (decl.isPrivate()) {
            return "private";
        }
        return "package";
    }

    /**
     * Returns the effective visibility level of a method declaration.
     *
     * @param decl the method declaration
     * @return one of "public", "protected", "package", or "private"
     */
    private static String effectiveVisibility(MethodDeclaration decl) {
        if (decl.isPublic()) {
            return "public";
        }
        if (decl.isProtected()) {
            return "protected";
        }
        if (decl.isPrivate()) {
            return "private";
        }
        return "package";
    }

    /**
     * Returns the effective visibility level of a field declaration.
     *
     * @param decl the field declaration
     * @return one of "public", "protected", "package", or "private"
     */
    private static String effectiveVisibility(FieldDeclaration decl) {
        if (decl.isPublic()) {
            return "public";
        }
        if (decl.isProtected()) {
            return "protected";
        }
        if (decl.isPrivate()) {
            return "private";
        }
        return "package";
    }

    /**
     * Checks if a class or interface declaration matches any of the configured visibility tokens.
     *
     * @param decl the class or interface declaration
     * @param tokens the set of visibility tokens to match
     * @return true if the declaration's effective visibility is in the token set
     */
    private static boolean matchesVisibility(ClassOrInterfaceDeclaration decl, Set<String> tokens) {
        return tokens.contains(effectiveVisibility(decl));
    }

    /**
     * Checks if a method declaration matches any of the configured visibility tokens.
     *
     * @param decl the method declaration
     * @param tokens the set of visibility tokens to match
     * @return true if the declaration's effective visibility is in the token set
     */
    private static boolean matchesVisibility(MethodDeclaration decl, Set<String> tokens) {
        return tokens.contains(effectiveVisibility(decl));
    }

    /**
     * Checks if a field declaration matches any of the configured visibility tokens.
     *
     * @param decl the field declaration
     * @param tokens the set of visibility tokens to match
     * @return true if the declaration's effective visibility is in the token set
     */
    private static boolean matchesVisibility(FieldDeclaration decl, Set<String> tokens) {
        return tokens.contains(effectiveVisibility(decl));
    }

    private List<Finding> validateDocComments(Path root, Path file, String severity, Set<String> visibilityTokens) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .flatMap(cls -> {
                        final Stream.Builder<Finding> builder = Stream.builder();
                        if (matchesVisibility(cls, visibilityTokens) && !cls.getJavadoc().isPresent()) {
                            builder.add(Finding.of(severity, CATEGORY, root.relativize(file) + ":" + cls.getBegin().map(p -> p.line).orElse(-1) + ": public class missing Javadoc"));
                        }
                        cls.getMethods().stream()
                                .filter(m -> matchesVisibility(m, visibilityTokens))
                                .filter(m -> !m.getJavadoc().isPresent())
                                .forEach(m -> builder.add(Finding.of(severity, CATEGORY, root.relativize(file) + ":" + m.getBegin().map(p -> p.line).orElse(-1) + ": public method missing Javadoc")));
                        cls.getFields().stream()
                                .filter(f -> matchesVisibility(f, visibilityTokens))
                                .filter(f -> !f.getJavadoc().isPresent())
                                .forEach(f -> builder.add(Finding.of(severity, CATEGORY, root.relativize(file) + ":" + f.getBegin().map(p -> p.line).orElse(-1) + ": public field missing Javadoc")));
                        return builder.build();
                    })
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
