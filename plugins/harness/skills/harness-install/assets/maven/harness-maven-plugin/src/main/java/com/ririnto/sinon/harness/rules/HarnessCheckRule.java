package com.ririnto.sinon.harness.rules;

import com.ririnto.sinon.harness.Finding;
import com.ririnto.sinon.harness.core.RuleContext;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Strategy interface implemented by each harness validation rule.
 */
public interface HarnessCheckRule {
    /**
     * Gets the manifest category key used by this rule.
     *
     * @return the manifest category key
     */
    String category();

    /**
     * Determines whether this rule applies to the given context.
     *
     * @param ctx shared rule context
     * @return true if this rule should be validated
     */
    boolean applies(RuleContext ctx);

    /**
     * Validates the given project directory against this rule.
     *
     * @param ctx shared rule context
     * @return a collection of findings; empty if validation passes
     * @throws MojoExecutionException if validation fails
     */
    Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException;

    /**
     * Auto-formats the project against this rule, when supported.
     *
     * Rules without an automatic fix MUST keep this default and return an empty collection.
     *
     * @param ctx shared rule context
     * @return a collection of absolute paths modified by this rule
     * @throws MojoExecutionException if formatting fails
     */
    default Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        return List.of();
    }
}
