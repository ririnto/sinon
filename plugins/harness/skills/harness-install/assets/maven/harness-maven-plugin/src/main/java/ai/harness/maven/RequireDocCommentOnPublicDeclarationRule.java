package ai.harness.maven;

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
public class RequireDocCommentOnPublicDeclarationRule implements HarnessCheckRule {
    private static final String CATEGORY = "requireDocCommentOnPublicDeclaration";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        try {
            List<Path> sources = HarnessCheckHelper.stackSources(manifest, CATEGORY);
            return sources.stream()
                    .flatMap(file -> validateDocComments(root, file, severity).stream())
                    .toList();
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to enumerate Java sources: " + e.getMessage()));
        }
    }

    private List<Finding> validateDocComments(Path root, Path file, String severity) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            List<Finding> findings = new java.util.ArrayList<>();
            cu.walk(ClassOrInterfaceDeclaration.class, cls -> {
                if (cls.isPublic() && !cls.getJavadoc().isPresent()) {
                    int line = cls.getBegin().map(p -> p.line).orElse(-1);
                    findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": public class missing Javadoc"));
                }
                cls.getMethods().forEach(m -> {
                    if (m.isPublic() && !m.getJavadoc().isPresent()) {
                        int line = m.getBegin().map(p -> p.line).orElse(-1);
                        findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": public method missing Javadoc"));
                    }
                });
                cls.getFields().forEach(f -> {
                    if (f.isPublic() && !f.getJavadoc().isPresent()) {
                        int line = f.getBegin().map(p -> p.line).orElse(-1);
                        findings.add(new Finding(severity, CATEGORY, root.relativize(file) + ":" + line + ": public field missing Javadoc"));
                    }
                });
            });
            return findings;
        } catch (IOException e) {
            return List.of(new Finding(severity, CATEGORY, "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
        }
    }
}
