package com.ririnto.sinon.harness.rules.text;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Rule that requires hook files to be executable.
 */
public enum HookExecutableRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "hookExecutable";

    @Override
    public String category() {
        return "hookExecutable";
    }

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("hooks")).stream()
                .filter(hook -> HarnessCheckHelper.isSafeRegularFile(root, root.resolve(hook)))
                .filter(hook -> !Files.isExecutable(root.resolve(hook)))
                .map(hook -> Finding.of(severity, CATEGORY, hook + " must be executable"))
                .toList();
    }

    /**
     * Sets hook files to be executable if they are not already.
     */
    @Override
    public Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode catNode = manifest.get(CATEGORY);
        if (catNode == null) {
            return List.of();
        }
        final JsonNode parametersNode = catNode.get("parameters");
        if (parametersNode == null) {
            return List.of();
        }
        final JsonNode hooksNode = parametersNode.get("hooks");
        if (hooksNode == null) {
            return List.of();
        }
        final List<Path> formatted = new java.util.ArrayList<>();
        try {
            for (String hook : HarnessCheckHelper.extractPaths(hooksNode)) {
                final Path hookPath = root.resolve(hook);
                if (HarnessCheckHelper.isSafeRegularFile(root, hookPath) && !Files.isExecutable(hookPath)) {
                    final Set<PosixFilePermission> perms = EnumSet.copyOf(Files.getPosixFilePermissions(hookPath));
                    perms.add(PosixFilePermission.OWNER_EXECUTE);
                    perms.add(PosixFilePermission.GROUP_EXECUTE);
                    perms.add(PosixFilePermission.OTHERS_EXECUTE);
                    Files.setPosixFilePermissions(hookPath, perms);
                    formatted.add(hookPath);
                }
            }
        } catch (java.io.IOException e) {
            throw new MojoExecutionException("Failed to set hook executable permissions", e);
        }
        return formatted;
    }
}
