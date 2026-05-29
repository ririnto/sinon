package com.ririnto.sinon.harness.core;

import com.ririnto.sinon.harness.Finding;
import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Default Maven rule context implementation.
 */
public final class DefaultRuleContext implements RuleContext {
    private final Path root;
    private final Manifest manifest;
    private final String stack;

    /**
     * Creates a rule context.
     *
     * @param root absolute project root
     * @param manifest parsed manifest facade
     */
    public DefaultRuleContext(Path root, Manifest manifest) {
        this(root, manifest, "java");
    }

    /**
     * Creates a rule context with a specified stack.
     *
     * @param root absolute project root
     * @param manifest parsed manifest facade
     * @param stack stack identifier
     */
    public DefaultRuleContext(Path root, Manifest manifest, String stack) {
        this.root = root;
        this.manifest = manifest;
        this.stack = stack;
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public Manifest manifest() {
        return manifest;
    }

    @Override
    public String stack() {
        return stack;
    }

    @Override
    public String readSafe(Path path) throws MojoExecutionException {
        return HarnessCheckHelper.readFile(root, path);
    }

    @Override
    public WalkResult walkSafe(Path base) throws MojoExecutionException {
        return new WalkResult(HarnessCheckHelper.safeFileOrWalk(root, base), List.of());
    }


    @Override
    public boolean isAllowedRootContractSymlink(Path path) {
        return HarnessCheckHelper.allowedRootContractTarget(root, path) != null;
    }

    @Override
    public List<Path> stackSources(String category) throws MojoExecutionException {
        try {
            return HarnessCheckHelper.stackSources(manifest.raw(), category, stack, root);
        } catch (IOException e) {
            throw new MojoExecutionException("failed to collect stack sources: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Finding> stackSourceFindings(String category) throws MojoExecutionException {
        try {
            return HarnessCheckHelper.stackSourceFindings(manifest.raw(), category, stack, root);
        } catch (IOException e) {
            throw new MojoExecutionException("failed to collect stack source findings: " + e.getMessage(), e);
        }
    }
}
