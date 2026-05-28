package com.ririnto.sinon.harness.rules.ast;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Rule that requires JVM class members to follow the configured manifest order.
 */
public enum ClassMemberOrderingRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "classMemberOrdering";
    private static final List<String> DEFAULT_KIND_ORDER = List.of("companionObject", "constProperty", "fieldOrProperty", "initializer", "constructor", "function", "interface", "class", "enum");
    private static final List<String> DEFAULT_VISIBILITY_ORDER = List.of("public", "protected", "package", "private");
    private static final List<String> DEFAULT_OVERRIDE_ORDER = List.of("override", "nonOverride");

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final JsonNode messages = manifest.get(CATEGORY).get("messages");
        final Map<String, Integer> rankByKind = configuredRankMap(manifest);
        try {
            final List<Path> sources = ctx.stackSources(CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateFile(root, file, severity, messages, rankByKind).stream())
                    .toList();
        } catch (MojoExecutionException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to enumerate sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateFile(Path root, Path file, String severity, JsonNode messages, Map<String, Integer> rankByKind) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return Stream.concat(
                    cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                            .flatMap(type -> validateMembers(root, file, severity, messages, rankByKind, type.getNameAsString(), type.getMembers()).stream()),
                    cu.findAll(EnumDeclaration.class).stream()
                            .flatMap(type -> validateMembers(root, file, severity, messages, rankByKind, type.getNameAsString(), type.getMembers()).stream()))
                    .toList();
        } catch (IOException e) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }

    private List<Finding> validateMembers(Path root, Path file, String severity, JsonNode messages, Map<String, Integer> rankByKind, String className, List<BodyDeclaration<?>> members) {
        final int[] highestRank = {-1};
        return members.stream()
                .filter(member -> memberKind(member) != null)
                .flatMap(member -> {
                    final String kind = memberKind(member);
                    final String visibility = memberVisibility(member);
                    final String overrideState = memberOverrideState(member);
                    final int rank = rankByKind.getOrDefault(overrideState + ":" + visibility + ":" + kind, rankByKind.getOrDefault(visibility + ":" + kind, rankByKind.getOrDefault(kind, rankByKind.size())));
                    if (rank < highestRank[0]) {
                        return Stream.of(Finding.of(severity, CATEGORY, message(root, file, messages, className, memberName(member), overrideState, visibility, kind, member.getBegin().map(pos -> pos.line).orElse(-1))));
                    }
                    highestRank[0] = rank;
                    return Stream.<Finding>empty();
                })
                .toList();
    }

    private static Map<String, Integer> configuredRankMap(JsonNode manifest) {
        final JsonNode parameters = manifest.get(CATEGORY).get("parameters");
        final List<String> kindOrder = parameters != null && parameters.get("kindOrder") != null
                ? HarnessCheckHelper.extractPaths(parameters.get("kindOrder"))
                : DEFAULT_KIND_ORDER;
        final List<String> visibilityOrder = parameters != null && parameters.get("visibilityOrder") != null
                ? HarnessCheckHelper.extractPaths(parameters.get("visibilityOrder"))
                : DEFAULT_VISIBILITY_ORDER;
        final List<String> overrideOrder = parameters != null && parameters.get("overrideOrder") != null
                ? HarnessCheckHelper.extractPaths(parameters.get("overrideOrder"))
                : DEFAULT_OVERRIDE_ORDER;
        return IntStream.range(0, kindOrder.size() * visibilityOrder.size() * overrideOrder.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> overrideOrder.get(i % overrideOrder.size()) + ":" + visibilityOrder.get((i / overrideOrder.size()) % visibilityOrder.size()) + ":" + kindOrder.get(i / (overrideOrder.size() * visibilityOrder.size())),
                        Function.identity(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private static String memberVisibility(BodyDeclaration<?> member) {
        if (member instanceof FieldDeclaration field) {
            return visibility(field.isPrivate(), field.isProtected(), field.isPublic());
        }
        if (member instanceof ConstructorDeclaration constructor) {
            return visibility(constructor.isPrivate(), constructor.isProtected(), constructor.isPublic());
        }
        if (member instanceof MethodDeclaration method) {
            return visibility(method.isPrivate(), method.isProtected(), method.isPublic());
        }
        if (member instanceof ClassOrInterfaceDeclaration type) {
            return visibility(type.isPrivate(), type.isProtected(), type.isPublic());
        }
        if (member instanceof EnumDeclaration type) {
            return visibility(type.isPrivate(), type.isProtected(), type.isPublic());
        }
        return "package";
    }

    private static String visibility(boolean isPrivate, boolean isProtected, boolean isPublic) {
        if (isPrivate) {
            return "private";
        }
        if (isProtected) {
            return "protected";
        }
        if (isPublic) {
            return "public";
        }
        return "package";
    }

    private static String memberOverrideState(BodyDeclaration<?> member) {
        return member instanceof MethodDeclaration method && method.getAnnotationByName("Override").isPresent() ? "override" : "nonOverride";
    }

    private static String memberKind(BodyDeclaration<?> member) {
        if (member instanceof FieldDeclaration field) {
            if (field.isStatic() && field.isFinal()) {
                return "constProperty";
            }
            return "fieldOrProperty";
        }
        if (member instanceof InitializerDeclaration) {
            return "initializer";
        }
        if (member instanceof ConstructorDeclaration) {
            return "constructor";
        }
        if (member instanceof MethodDeclaration) {
            return "function";
        }
        if (member instanceof ClassOrInterfaceDeclaration type) {
            if (type.isInterface()) {
                return "interface";
            }
            return "class";
        }
        if (member instanceof EnumDeclaration) {
            return "enum";
        }
        return null;
    }

    private static String memberName(BodyDeclaration<?> member) {
        if (member instanceof FieldDeclaration field) {
            return field.getVariables().isEmpty() ? "field" : field.getVariable(0).getNameAsString();
        }
        if (member instanceof ConstructorDeclaration constructor) {
            return constructor.getNameAsString();
        }
        if (member instanceof MethodDeclaration method) {
            return method.getNameAsString();
        }
        if (member instanceof ClassOrInterfaceDeclaration type) {
            return type.getNameAsString();
        }
        if (member instanceof EnumDeclaration type) {
            return type.getNameAsString();
        }
        return "member";
    }

    private static String message(Path root, Path file, JsonNode messages, String className, String memberName, String memberOverrideState, String memberVisibility, String memberKind, int line) {
        return (messages != null && messages.get("default") != null
                ? messages.get("default").asString()
                : "{file}:{line}: class `{className}` member `{memberName}` ({memberKind}) is out of order")
                .replace("{file}", root.relativize(file).toString())
                .replace("{line}", Integer.toString(line))
                .replace("{className}", className)
                .replace("{memberName}", memberName)
                .replace("{memberOverrideState}", memberOverrideState)
                .replace("{memberVisibility}", memberVisibility)
                .replace("{memberKind}", memberKind);
    }
}
