package ai.harness.maven.rules;

import ai.harness.maven.Finding;
import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.regex.Pattern;
import java.util.Collectors;

/**
 * Helper class containing shared validation utilities.
 */
public class HarnessCheckHelper {
    private HarnessCheckHelper() {
    }

    /**
     * Gets the severity level for a category from the manifest.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @return severity level: "WARN", "INFO", or "ERROR" (default)
     */
    public static String getSeverity(JsonNode manifest, String category) {
        JsonNode catNode = manifest.get(category);
        if (catNode != null && catNode.has("severity")) {
            String sev = catNode.get("severity").asText();
            if ("WARN".equals(sev) || "INFO".equals(sev)) {
                return sev;
            }
        }
        return "ERROR";
    }

    /**
     * Reads a file, following symlinks if allowed.
     *
     * @param root the project root directory
     * @param path the file path to read
     * @return the file contents as a string
     * @throws MojoExecutionException if reading fails
     */
    public static String readFile(Path root, Path path) throws MojoExecutionException {
        if (Files.isSymbolicLink(path)) {
            Path allowed = allowedRootContractTarget(root, path);
            if (allowed == null) {
                throw new MojoExecutionException("symlink not allowed: " + path);
            }
            path = allowed;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("failed to read " + path, e);
        }
    }

    /**
     * Checks if a path is a safe regular file (not a disallowed symlink).
     *
     * @param root the project root directory
     * @param path the file path to check
     * @return true if path is a safe regular file
     */
    public static boolean isSafeRegularFile(Path root, Path path) {
        if (Files.isSymbolicLink(path)) {
            return allowedRootContractTarget(root, path) != null;
        }
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Checks if a path is a safe directory (never a symlink).
     *
     * @param root the project root directory
     * @param path the directory path to check
     * @return true if path is a safe directory
     */
    public static boolean isSafeDirectory(Path root, Path path) {
        if (Files.isSymbolicLink(path)) {
            return false;
        }
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Lists files in a directory or returns a single file if path is a file.
     *
     * @param root the project root directory
     * @param base the base path (file or directory)
     * @return list of regular files
     * @throws MojoExecutionException if inspection fails
     */
    public static List<Path> safeFileOrWalk(Path root, Path base) throws MojoExecutionException {
        if (Files.isSymbolicLink(base) && allowedRootContractTarget(root, base) == null) {
            return List.of();
        }
        if (isSafeRegularFile(root, base)) {
            return List.of(base);
        }
        if (!Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(base)) {
            return stream
                    .filter(f -> !f.equals(base) && !Files.isSymbolicLink(f) && Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS))
                    .toList();
        } catch (IOException error) {
            throw new MojoExecutionException("failed to inspect " + base, error);
        }
    }

    /**
     * Resolves a symlink target if it's allowed by the contract.
     *
     * @param root the project root directory
     * @param path the symlink path
     * @return the resolved target path if allowed, null otherwise
     */
    public static Path allowedRootContractTarget(Path root, Path path) {
        if (!Files.isSymbolicLink(path)) {
            return null;
        }
        Path normalized = root.normalize();
        Path fileName = path.getFileName();
        if (fileName == null || path.getParent() == null || !path.getParent().normalize().equals(normalized)) {
            return null;
        }
        String name = fileName.toString();
        if (!name.equals("AGENTS.md") && !name.equals("CLAUDE.md")) {
            return null;
        }
        String expected = name.equals("AGENTS.md") ? "CLAUDE.md" : "AGENTS.md";
        try {
            Path target = Files.readSymbolicLink(path);
            if (target.getNameCount() != 1 || !target.toString().equals(expected)) {
                return null;
            }
            Path resolved = normalized.resolve(target).normalize();
            if (resolved.getParent().equals(normalized) && !Files.isSymbolicLink(resolved) && Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) {
                return resolved;
            }
        } catch (IOException e) {
        }
        return null;
    }

    /**
     * Extracts string values from a JSON array node.
     *
     * @param node the JSON array node
     * @return list of string values; empty if node is null or not an array
     */
    public static List<String> extractPaths(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return Stream.of(node.spliterator(), false).map(JsonNode::asText).toList();
    }

    /**
     * Collects all Java source files from roots and extensions specified in the manifest.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @return list of source file paths
     * @throws IOException if walking fails
     */
    public static List<Path> stackSources(JsonNode manifest, String category) throws IOException {
        JsonNode catNode = manifest.get(category);
        if (catNode == null) {
            return Collections.emptyList();
        }
        JsonNode params = catNode.get("parameters");
        if (params == null) {
            return Collections.emptyList();
        }
        JsonNode rootsNode = params.get("sourceRootsPerStack");
        JsonNode extsNode = params.get("extensionsPerStack");
        if (rootsNode == null || extsNode == null) {
            return Collections.emptyList();
        }
        JsonNode javaRoots = rootsNode.get("java");
        JsonNode javaExts = extsNode.get("java");
        if (javaRoots == null || javaExts == null) {
            return Collections.emptyList();
        }
        Set<String> extensions = StreamSupport.stream(javaExts.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
        FileSystem fs = FileSystems.getDefault();
        return StreamSupport.stream(javaRoots.spliterator(), false)
                .map(JsonNode::asText)
                .flatMap(rootEntry -> walkRoot(fs, Path.of("."), rootEntry, extensions).stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Walks a root directory matching file extensions.
     *
     * @param fs the file system
     * @param base the base path
     * @param rootEntry the root entry (may contain wildcards)
     * @param extensions the set of allowed extensions
     * @return list of matching files
     */
    public static List<Path> walkRoot(FileSystem fs, Path base, String rootEntry, Set<String> extensions) {
        try {
            if (rootEntry.contains("*")) {
                String pattern = "glob:" + rootEntry + "/**/*";
                var matcher = fs.getPathMatcher(pattern);
                try (Stream<Path> stream = Files.walk(base)) {
                    return stream
                            .filter(p -> matcher.matches(p) && extensions.contains(extensionOf(p)) && !containsSegment(p, "target") && !containsSegment(p, "build"))
                            .collect(Collectors.toList());
                }
            } else {
                Path resolved = base.resolve(rootEntry);
                if (!Files.exists(resolved)) {
                    return Collections.emptyList();
                }
                try (Stream<Path> stream = Files.walk(resolved)) {
                    return stream
                            .filter(p -> extensions.contains(extensionOf(p)) && !containsSegment(p, "target") && !containsSegment(p, "build"))
                            .collect(Collectors.toList());
                }
            }
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Gets the file extension of a path.
     *
     * @param p the path
     * @return the extension without the dot, or empty string if none
     */
    public static String extensionOf(Path p) {
        String name = p.getFileName().toString();
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1);
    }

    /**
     * Checks if a path contains a specific segment in its path components.
     *
     * @param p the path
     * @param segment the segment to search for
     * @return true if the segment is found
     */
    public static boolean containsSegment(Path p, String segment) {
        for (int i = 0; i < p.getNameCount(); i++) {
            if (p.getName(i).toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether a rule applies to the given manifest.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @return true if the category is enabled (default) or has no enabled field
     */
    public static boolean applies(JsonNode manifest, String category) {
        JsonNode node = manifest.get(category);
        if (node == null || !node.isObject()) {
            return false;
        }
        JsonNode enabled = node.get("enabled");
        return enabled == null || !enabled.isBoolean() || enabled.asBoolean();
    }
}
