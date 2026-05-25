package com.ririnto.sinon.harness.rules.fs;

import com.ririnto.sinon.harness.rules.HarnessCheckHelper;
import com.ririnto.sinon.harness.rules.HarnessCheckRule;
import com.ririnto.sinon.harness.core.RuleContext;
import com.ririnto.sinon.harness.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Rule that requires .gitkeep or real files in empty directories.
 */
public enum EmptyDirectoryPlaceholdersRule implements HarnessCheckRule {
    INSTANCE;

    @Override
    public String category() {
        return "emptyDirectoryPlaceholders";
    }
    private static final String CATEGORY = "emptyDirectoryPlaceholders";

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
        return HarnessCheckHelper.extractPaths(catNode.get("parameters").get("directories")).stream()
                .flatMap(dir -> validateKeepFile(root, dir, severity).stream())
                .toList();
    }

    private List<Finding> validateKeepFile(Path root, String dir, String severity) {
        final Path dirPath = root.resolve(dir);
        return Stream.of(dirPath)
                .filter(p -> HarnessCheckHelper.isSafeDirectory(root, p))
                .flatMap(p -> {
                    try (final Stream<Path> stream = Files.list(p)) {
                        final List<Path> realFiles = stream
                                .filter(f -> !Files.isSymbolicLink(f))
                                .filter(f -> !f.getFileName().toString().equals(".gitkeep"))
                                .toList();
                        return realFiles.isEmpty() && !HarnessCheckHelper.isSafeRegularFile(root, p.resolve(".gitkeep"))
                                ? Stream.of(Finding.of(severity, CATEGORY, "empty directory must keep placeholder or real files: " + dir))
                                : Stream.empty();
                    } catch (IOException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }
}
