package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Rule that requires Javadoc on public declarations.
 */
public enum RequireDocCommentOnPublicDeclarationRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "requireDocCommentOnPublicDeclaration";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            final List<Path> sources = HarnessCheckHelper.stackSources(manifest, CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateDocComments(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateDocComments(Path root, Path file, String severity) {
        try {
            final CompilationUnit cu = StaticJavaParser.parse(file);
            return cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .flatMap(cls -> {
                        final java.util.List<Finding> findings = new java.util.ArrayList<>();
                        if (cls.isPublic() && !cls.getJavadoc().isPresent()) {
                            findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + cls.getBegin().map(p -> p.line).orElse(-1) + ": public class missing Javadoc"));
                        }
                        findings.addAll(cls.getMethods().stream()
                                .filter(m -> m.isPublic() && !m.getJavadoc().isPresent())
                                .map(m -> new Finding(severity, CATEGORY, root.relativize(file) + ":" + m.getBegin().map(p -> p.line).orElse(-1) + ": public method missing Javadoc"))
                                .toList());
                        findings.addAll(cls.getFields().stream()
                                .filter(f -> f.isPublic() && !f.getJavadoc().isPresent())
                                .map(f -> new Finding(severity, CATEGORY, root.relativize(file) + ":" + f.getBegin().map(p -> p.line).orElse(-1) + ": public field missing Javadoc"))
                                .toList());
                        return findings.stream();
                    })
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
