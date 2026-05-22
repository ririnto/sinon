package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Strategy interface implemented by each harness validation rule.
 */
public interface HarnessCheckRule {
    /**
     * Determines whether this rule applies to the given manifest.
     *
     * @param manifest the manifest JSON node
     * @return true if this rule should be validated
     */
    boolean applies(JsonNode manifest);

    /**
     * Validates the given project directory against this rule.
     *
     * @param projectDir the project root directory
     * @param manifest the manifest JSON node
     * @return a collection of findings; empty if validation passes
     * @throws MojoExecutionException if validation fails
     */
    Collection<Finding> validate(Path projectDir, JsonNode manifest) throws MojoExecutionException;
}
