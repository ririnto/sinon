package com.ririnto.sinon.harness.core;

import com.ririnto.sinon.harness.Finding;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

/**
 * Shared runtime context passed to Maven harness rules.
 */
public interface RuleContext {
    /**
     * Returns the absolute project root.
     *
     * @return project root path
     */
    Path root();

    /**
     * Returns the parsed manifest facade.
     *
     * @return manifest facade
     */
    Manifest manifest();

    /**
     * Returns the stack identifier for this context.
     *
     * @return stack name, such as "java"
     */
    String stack();

    /**
     * Reads a safe file path.
     *
     * @param path file path
     * @return file text
     * @throws MojoExecutionException when reading fails
     */
    String readSafe(Path path) throws MojoExecutionException;

    /**
     * Walks a safe file or directory.
     *
     * @param base file or directory path
     * @return walk result
     * @throws MojoExecutionException when walking fails
     */
    WalkResult walkSafe(Path base) throws MojoExecutionException;

    /**
     * Returns whether a symlink is allowed by the root contract.
     *
     * @param path path to inspect
     * @return true when the symlink is allowed
     */
    boolean isAllowedRootContractSymlink(Path path);

    /**
     * Returns source file paths for a manifest category and this context's stack.
     *
     * @param category manifest category name
     * @return list of source file paths
     * @throws MojoExecutionException when collection fails
     */
    List<Path> stackSources(String category) throws MojoExecutionException;

    /**
     * Collects findings for source root entries that are unsafe for a manifest category.
     *
     * @param category manifest category name
     * @return list of findings
     * @throws MojoExecutionException when validation fails
     */
    List<Finding> stackSourceFindings(String category) throws MojoExecutionException;

    /**
     * Safe walk result with filesystem findings.
     *
     * @param files safe files discovered
     * @param findings filesystem findings discovered while walking
     */
    record WalkResult(Collection<Path> files, Collection<Finding> findings) {
    }
}
