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

    private static final String CATEGORY = "emptyDirectoryPlaceholders";

    @Override
    public String category() {
        return "emptyDirectoryPlaceholders";
    }

    @Override
    public boolean applies(RuleContext ctx) {
        return ctx.manifest().isEnabled(CATEGORY);
    }

    @Override
    public Collection<Finding> validate(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        return HarnessCheckHelper.extractPaths(manifest.get(CATEGORY).get("parameters").get("directories")).stream()
                .flatMap(dir -> validateKeepFile(root, dir, severity).stream())
                .toList();
    }

    /**
     * Creates .gitkeep placeholder files in empty directories that lack them.
     * Returns the collection of paths where .gitkeep was created.
     */
    @Override
    public Collection<Path> format(RuleContext ctx) throws MojoExecutionException {
        final Path root = ctx.root();
        final JsonNode manifest = ctx.manifest().raw();
        final JsonNode categoryNode = manifest.get(CATEGORY);
        if (categoryNode == null || categoryNode.get("parameters") == null || categoryNode.get("parameters").get("directories") == null) {
            return List.of();
        }
        return HarnessCheckHelper.extractPaths(categoryNode.get("parameters").get("directories")).stream()
                .flatMap(dir -> createGitkeepIfNeeded(root, dir).stream())
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

    private List<Path> createGitkeepIfNeeded(Path root, String dir) throws MojoExecutionException {
        final Path dirPath = root.resolve(dir);
        if (!HarnessCheckHelper.isSafeDirectory(root, dirPath)) {
            return List.of();
        }
        try (final Stream<Path> stream = Files.list(dirPath)) {
            final List<Path> realFiles = stream
                    .filter(f -> !Files.isSymbolicLink(f))
                    .filter(f -> !f.getFileName().toString().equals(".gitkeep"))
                    .toList();
            if (realFiles.isEmpty() && !HarnessCheckHelper.isSafeRegularFile(root, dirPath.resolve(".gitkeep"))) {
                final Path gitkeepPath = dirPath.resolve(".gitkeep");
                Files.createFile(gitkeepPath);
                return List.of(gitkeepPath);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create .gitkeep in " + dir, e);
        }
        return List.of();
    }
}
