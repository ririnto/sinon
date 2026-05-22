package ai.harness.maven.rules;

import ai.harness.maven.Finding;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rule that forbids unsafe symlinks in the project root.
 */
public enum ForbidUnsafeSymlinksRule implements HarnessCheckRule {
    INSTANCE;
    private static final String CATEGORY = "forbidUnsafeSymlinks";

    @Override
    public boolean applies(JsonNode manifest) {
        return HarnessCheckHelper.applies(manifest, CATEGORY);
    }

    @Override
    public Collection<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
        final JsonNode catNode = manifest.get(CATEGORY);
        final JsonNode allowedNode = catNode.get("parameters").get("allowedSymlinkPairs");
        final Set<String> allowedNames = java.util.stream.StreamSupport.stream(allowedNode.spliterator(), false)
                .flatMap(pair -> java.util.stream.Stream.of(pair.get(0).asText(), pair.get(1).asText()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        final String severity = HarnessCheckHelper.getSeverity(manifest, CATEGORY);
        final List<String> rootBases = List.of("AGENTS.md", "CLAUDE.md", "ARCHITECTURE.md", "docs", ".claude", ".github");
        return rootBases.stream()
                .flatMap(baseName -> {
                    try {
                        final Path base = root.resolve(baseName);
                        return validateSymlinks(root, base, allowedNames, severity).stream();
                    } catch (MojoExecutionException e) {
                        return Stream.empty();
                    }
                })
                .toList();
    }

    private List<Finding> validateSymlinks(Path root, Path base, Set<String> allowedNames, String severity) throws MojoExecutionException {
        if (Files.isSymbolicLink(base)) {
            final String name = base.getFileName().toString();
            return !allowedNames.contains(name) || HarnessCheckHelper.allowedRootContractTarget(root, base) == null
                    ? List.of(new Finding(severity, CATEGORY, "symlink scan root is not allowed: " + root.relativize(base)))
                    : List.of();
        }
        return HarnessCheckHelper.safeFileOrWalk(root, base).stream()
                .filter(Files::isSymbolicLink)
                .flatMap(file -> {
                    final String name = file.getFileName().toString();
                    return !allowedNames.contains(name) || HarnessCheckHelper.allowedRootContractTarget(root, file) == null
                            ? Stream.of(new Finding(severity, CATEGORY, "symlink " + (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) ? "directory" : "file") + " is not allowed: " + root.relativize(file)))
                            : Stream.empty();
                })
                .toList();
    }
}
