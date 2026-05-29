package com.ririnto.sinon.harness.rules.fs;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Rule that forbids unsafe symlinks in the project root.
 */
public enum SymlinkSafetyRule implements HarnessCheckRule {
    INSTANCE;

    private static final String CATEGORY = "symlinkSafety";

    @Override
    public String category() {
        return "symlinkSafety";
    }

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final Set<String> allowedNames = StreamSupport.stream(manifest.get(CATEGORY).get("parameters").get("allowedSymlinkPairs").spliterator(), false)
                .flatMap(pair -> Stream.of(pair.get(0).asString(), pair.get(1).asString()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return List.of("AGENTS.md", "CLAUDE.md", "ARCHITECTURE.md", "docs", ".claude", ".github").stream()
                .flatMap(baseName -> validateScanRoot(root, root.resolve(baseName), allowedNames, severity).stream())
                .toList();
    }

    private List<Finding> validateScanRoot(Path root, Path base, Set<String> allowedNames, String severity) {
        return validateSymlinks(root, base, allowedNames, severity);
    }

    private List<Finding> validateSymlinks(Path root, Path base, Set<String> allowedNames, String severity) {
        if (Files.isSymbolicLink(base)) {
            final String name = base.getFileName().toString();
            return !allowedNames.contains(name) || HarnessCheckHelper.allowedRootContractTarget(root, base) == null
                    ? List.of(Finding.of(severity, CATEGORY, "symlink scan root is not allowed: " + root.relativize(base)))
                    : List.of();
        }
        final HarnessCheckHelper.SymlinkScanResult scanResult = HarnessCheckHelper.symlinkScanResult(root, base);
        final List<Finding> allFindings = new ArrayList<>(scanResult.findings());
        scanResult.symlinks().stream()
                .flatMap(file -> {
                    final String name = file.getFileName().toString();
                    return !allowedNames.contains(name) || HarnessCheckHelper.allowedRootContractTarget(root, file) == null
                            ? Stream.of(Finding.of(severity, CATEGORY, "symlink " + (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) ? "directory" : "file") + " is not allowed: " + root.relativize(file)))
                            : Stream.empty();
                })
                .forEach(allFindings::add);
        return allFindings;
    }
}
