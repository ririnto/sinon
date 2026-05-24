package ai.harness.maven.rules;

import ai.harness.maven.Finding;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Rule that requires JVM class members to follow the configured manifest order.
 */
public enum ClassMemberOrderingRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "classMemberOrdering";
    }
    private static final String CATEGORY = "classMemberOrdering";
    private static final List<String> DEFAULT_ORDER = List.of("companionObject", "fieldOrProperty", "initializer", "constructor", "function", "nestedType");

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final JsonNode messages = manifest.get(CATEGORY).get("messages");
        final List<String> order = configuredOrder(manifest);
        final Map<String, Integer> rankByKind = ranks(order);
        try {
            final List<Path> sources = HarnessCheckHelper.stackSources(manifest, CATEGORY, "java");
            return sources.stream()
                    .flatMap(file -> validateFile(root, file, severity, messages, rankByKind).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateFile(Path root, Path file, String severity, JsonNode messages, Map<String, Integer> rankByKind) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return java.util.stream.Stream.concat(
                    cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                            .flatMap(type -> validateMembers(root, file, severity, messages, rankByKind, type.getNameAsString(), type.getMembers()).stream()),
                    cu.findAll(EnumDeclaration.class).stream()
                            .flatMap(type -> validateMembers(root, file, severity, messages, rankByKind, type.getNameAsString(), type.getMembers()).stream()))
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
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
                        return java.util.stream.Stream.of(new Finding(severity, CATEGORY, message(root, file, messages, className, memberName(member), overrideState, visibility, kind, member.getBegin().map(pos -> pos.line).orElse(-1))));
                    }
                    highestRank[0] = rank;
                    return java.util.stream.Stream.<Finding>empty();
                })
                .toList();
    }

    private static List<String> configuredOrder(JsonNode manifest) {
        final JsonNode parameters = manifest.get(CATEGORY).get("parameters");
        if (parameters == null || parameters.get("order") == null) {
            return DEFAULT_ORDER;
        }
        final List<String> order = HarnessCheckHelper.extractPaths(parameters.get("order"));
        return order.isEmpty() ? DEFAULT_ORDER : order;
    }

    private static Map<String, Integer> ranks(List<String> order) {
        final Map<String, Integer> ranks = new HashMap<>();
        IntStream.range(0, order.size()).forEach(index -> ranks.put(order.get(index), index));
        return ranks;
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
        if (member instanceof FieldDeclaration) {
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
        if (member instanceof ClassOrInterfaceDeclaration || member instanceof EnumDeclaration) {
            return "nestedType";
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
        final String template = messages != null && messages.get("default") != null
                ? messages.get("default").asText()
                : "{file}:{line}: class `{className}` member `{memberName}` ({memberKind}) is out of order";
        return template
                .replace("{file}", root.relativize(file).toString())
                .replace("{line}", Integer.toString(line))
                .replace("{className}", className)
                .replace("{memberName}", memberName)
                .replace("{memberOverrideState}", memberOverrideState)
                .replace("{memberVisibility}", memberVisibility)
                .replace("{memberKind}", memberKind);
    }
}
