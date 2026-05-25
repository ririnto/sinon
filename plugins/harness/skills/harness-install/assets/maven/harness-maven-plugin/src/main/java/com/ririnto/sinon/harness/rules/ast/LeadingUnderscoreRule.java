package com.ririnto.sinon.harness.rules.ast;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.ririnto.sinon.harness.Finding;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import org.apache.maven.plugin.MojoExecutionException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Rule that forbids leading underscores in Java file basenames and declarations.
 */
public enum LeadingUnderscoreRule implements AstRule {
    INSTANCE;

    private static final String CATEGORY = "leadingUnderscore";

    @Override
    public String category() {
        return CATEGORY;
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final RuleConfig ruleConfig = RuleConfig.from(manifest);
        return ctx.stackSources(CATEGORY).stream()
                .flatMap(file -> validateFile(ctx.root(), file, severity, ruleConfig).stream())
                .toList();
    }

    private List<Finding> validateFile(Path root, Path file, String severity, RuleConfig ruleConfig) {
        try {
            final CompilationUnit compilationUnit = StaticJavaParser.parse(file);
            final String basename = basename(file);
            final List<Finding> basenameFindings = ruleConfig.isForbidden(basename)
                    ? List.of(finding(root, file, severity, basename, 1))
                    : List.of();
            final List<Finding> declarationFindings = compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .map(ClassOrInterfaceDeclaration::getNameAsString)
                    .filter(ruleConfig::isForbidden)
                    .map(name -> finding(root, file, severity, name, 1))
                    .toList();
            final List<Finding> methodFindings = compilationUnit.findAll(MethodDeclaration.class).stream()
                    .filter(method -> ruleConfig.isForbidden(method.getNameAsString()))
                    .map(method -> finding(root, file, severity, method.getNameAsString(), method.getBegin().map(position -> position.line).orElse(1)))
                    .toList();
            final List<Finding> fieldFindings = compilationUnit.findAll(FieldDeclaration.class).stream()
                    .flatMap(field -> field.getVariables().stream())
                    .filter(variable -> ruleConfig.isForbidden(variable.getNameAsString()))
                    .map(variable -> finding(root, file, severity, variable.getNameAsString(), variable.getBegin().map(position -> position.line).orElse(1)))
                    .toList();
            final List<Finding> variableFindings = compilationUnit.findAll(VariableDeclarator.class).stream()
                    .filter(variable -> ruleConfig.isForbidden(variable.getNameAsString()))
                    .map(variable -> finding(root, file, severity, variable.getNameAsString(), variable.getBegin().map(position -> position.line).orElse(1)))
                    .toList();
            return Stream.of(basenameFindings, declarationFindings, methodFindings, fieldFindings, variableFindings)
                    .flatMap(List::stream)
                    .distinct()
                    .toList();
        } catch (IOException error) {
            return List.of(Finding.of(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + error.getMessage()));
        }
    }

    private static Finding finding(Path root, Path file, String severity, String name, int line) {
        return Finding.of(severity, CATEGORY, root.relativize(file) + ":" + line + ": declaration `" + name + "` uses a leading underscore");
    }

    private static String basename(Path file) {
        final String fileName = file.getFileName().toString();
        final int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private record RuleConfig(Set<String> allowedNames, List<Pattern> allowedPatterns) {
        static RuleConfig from(JsonNode manifest) {
            final JsonNode params = parameters(manifest);
            final JsonNode allowedNamesNode = params.get("allowedNames");
            final Set<String> names = Stream.concat(
                    Stream.of("_"),
                    allowedNamesNode != null && allowedNamesNode.isArray()
                            ? StreamSupport.stream(allowedNamesNode.spliterator(), false).map(JsonNode::asText)
                            : Stream.empty())
                    .collect(Collectors.toUnmodifiableSet());
            final JsonNode patternsNode = params.get("allowedPatterns");
            final List<Pattern> patterns = patternsNode != null && patternsNode.isArray()
                    ? StreamSupport.stream(patternsNode.spliterator(), false).map(JsonNode::asText).map(Pattern::compile).toList()
                    : List.of();
            return new RuleConfig(names, patterns);
        }

        boolean isForbidden(String name) {
            return name.startsWith("_") && !allowedNames.contains(name) && allowedPatterns.stream().noneMatch(pattern -> pattern.matcher(name).matches());
        }

        private static JsonNode parameters(JsonNode manifest) {
            final JsonNode section = manifest.get(CATEGORY);
            if (section == null || !section.has("parameters")) {
                return JsonNodeFactory.instance.objectNode();
            }
            return section.get("parameters");
        }
    }
}
