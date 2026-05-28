package com.ririnto.sinon.harness.rules;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

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
        final JsonNode catNode = manifest.get(category);
        return catNode != null && catNode.has("severity")
                ? (catNode.get("severity").asString().equals("WARN") || catNode.get("severity").asString().equals("INFO")
                        ? catNode.get("severity").asString()
                        : "ERROR")
                : "ERROR";
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
        final Path resolved = Files.isSymbolicLink(path) ? allowedRootContractTarget(root, path) : path;
        if (resolved == null) {
            throw new MojoExecutionException("symlink not allowed: " + path);
        }
        try {
            return Files.readString(resolved, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("failed to read " + resolved, e);
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
        return Files.isSymbolicLink(path)
                ? allowedRootContractTarget(root, path) != null
                : Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Checks if a path is a safe directory (never a symlink).
     *
     * @param root the project root directory
     * @param path the directory path to check
     * @return true if path is a safe directory
     */
    public static boolean isSafeDirectory(Path root, Path path) {
        return !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
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
        try (final Stream<Path> stream = Files.walk(base)) {
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
        final Path normalized = root.normalize();
        final Path fileName = path.getFileName();
        if (fileName == null || path.getParent() == null || !path.getParent().normalize().equals(normalized)) {
            return null;
        }
        final String name = fileName.toString();
        return (name.equals("AGENTS.md") || name.equals("CLAUDE.md"))
                ? allowedRootContractTarget(normalized, path, name.equals("AGENTS.md") ? "CLAUDE.md" : "AGENTS.md")
                : null;
    }

    /**
     * Extracts string values from a JSON array node.
     *
     * @param node the JSON array node
     * @return list of string values; empty if node is null or not an array
     */
    public static List<String> extractPaths(JsonNode node) {
        return node == null || !node.isArray()
                ? Collections.emptyList()
                : StreamSupport.stream(node.spliterator(), false)
                        .map(JsonNode::asString)
                        .toList();
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
        return stackSources(manifest, category, "java", Path.of("."));
    }

    /**
     * Collects all source files for a specific stack from roots and extensions specified in the manifest.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @param stack retained for signature compatibility; ignored under flat parameter shape
     * @return list of source file paths
     * @throws IOException if walking fails
     */
    public static List<Path> stackSources(JsonNode manifest, String category, String stack) throws IOException {
        return stackSources(manifest, category, stack, Path.of("."));
    }

    /**
     * Collects all source files for a category, honoring include and exclude patterns.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @param stack retained for signature compatibility; ignored under flat parameter shape
     * @param projectRoot the project root for relative path resolution
     * @return list of source file paths
     * @throws IOException if walking fails
     */
    public static List<Path> stackSources(JsonNode manifest, String category, String stack, Path projectRoot) throws IOException {
        final JsonNode categoryNode = manifest.get(category);
        final JsonNode params = categoryNode == null ? null : categoryNode.get("parameters");
        final JsonNode rootsNode = params == null ? null : params.get("sourceRoots");
        final JsonNode extsNode = params == null ? null : params.get("extensions");
        if (rootsNode == null || extsNode == null) {
            return Collections.emptyList();
        }
        final Set<String> extensions = StreamSupport.stream(extsNode.spliterator(), false)
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
        final List<String> includes = extractPaths(params.get("includePaths"));
        final List<String> excludes = extractPaths(params.get("excludePaths"));
        final FileSystem fs = FileSystems.getDefault();
        return StreamSupport.stream(rootsNode.spliterator(), false)
                .flatMap(rootNode -> walkRootSafely(fs, projectRoot, rootNode.asString(), extensions, includes, excludes))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Walks a root directory matching file extensions, with include/exclude filters.
     *
     * @param fs the file system
     * @param base the base path
     * @param rootEntry the root entry (may contain wildcards)
     * @param extensions the set of allowed extensions
     * @param includes glob patterns for inclusion (empty = match all)
     * @param excludes glob patterns for exclusion (empty = exclude none)
     * @return list of matching files
     * @throws IOException if directory walk fails
     */
    public static List<Path> walkRoot(FileSystem fs, Path base, String rootEntry, Set<String> extensions, List<String> includes, List<String> excludes) throws IOException {
        if (!isSafeRelativeRoot(rootEntry)) {
            return Collections.emptyList();
        }
        final List<PathMatcher> includeMatchers = includes.stream().map(p -> fs.getPathMatcher("glob:" + p)).toList();
        final List<PathMatcher> excludeMatchers = excludes.stream().map(p -> fs.getPathMatcher("glob:" + p)).toList();
        final Stream<Path> stream = rootEntry.contains("*")
                ? Files.walk(base).filter(p -> fs.getPathMatcher("glob:" + rootEntry + "/**/*").matches(p))
                : Files.walk(base.resolve(rootEntry));
        try (stream) {
            return stream
                    .filter(p -> !Files.isSymbolicLink(p) && Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> extensions.contains(extensionOf(p)))
                    .filter(p -> !containsSegment(p, "target") && !containsSegment(p, "build"))
                    .filter(p -> applyIncludeFilters(p, base, includeMatchers))
                    .filter(p -> applyExcludeFilters(p, base, excludeMatchers))
                    .map(p -> p.toAbsolutePath().normalize())
                    .toList();
        }
    }

    /**
     * Checks whether a manifest source root is target-relative and cannot escape upward.
     *
     * @param rootEntry the manifest source root entry
     * @return true when the root entry is a safe relative path or glob
     */
    public static boolean isSafeRelativeRootEntry(String rootEntry) {
        return isSafeRelativeRoot(rootEntry);
    }

    /**
     * Gets the file extension of a path.
     *
     * @param p the path
     * @return the extension without the dot, or empty string if none
     */
    public static String extensionOf(Path p) {
        final String name = p.getFileName().toString();
        final int idx = name.lastIndexOf('.');
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
        return IntStream.range(0, p.getNameCount())
                .anyMatch(i -> p.getName(i).toString().equals(segment));
    }

    /**
     * Determines whether a rule applies to the given manifest.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @return true if the category is enabled (default) or has no enabled field
     */
    public static boolean applies(JsonNode manifest, String category) {
        final JsonNode node = manifest.get(category);
        if (node == null || !node.isObject()) {
            return false;
        }
        final JsonNode enabled = node.get("enabled");
        return enabled == null || !enabled.isBoolean() || enabled.asBoolean();
    }

    /**
     * Resolves an already qualified root contract symlink target.
     *
     * @param normalized the normalized project root
     * @param path the symlink path
     * @param expected the expected one-segment target name
     * @return the resolved target path if allowed, null otherwise
     */
    private static Path allowedRootContractTarget(Path normalized, Path path, String expected) {
        try {
            final Path target = Files.readSymbolicLink(path);
            if (target.getNameCount() != 1 || !target.toString().equals(expected)) {
                return null;
            }
            final Path resolved = normalized.resolve(target).normalize();
            return resolved.getParent().equals(normalized) && !Files.isSymbolicLink(resolved) && Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)
                    ? resolved
                    : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Walks a root entry returning an empty stream when an IO error occurs.
     *
     * @param fs the file system
     * @param base the base path
     * @param rootEntry the manifest root entry
     * @param extensions allowed extensions
     * @param includes include patterns
     * @param excludes exclude patterns
     * @return stream of matching file paths
     */
    private static Stream<Path> walkRootSafely(FileSystem fs, Path base, String rootEntry, Set<String> extensions, List<String> includes, List<String> excludes) {
        try {
            return walkRoot(fs, base, rootEntry, extensions, includes, excludes).stream();
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    /**
     * Applies include filters to a file path.
     *
     * @param file the file path
     * @param base the base directory
     * @param matchers glob matchers for inclusion
     * @return true if matchers is empty or file matches any include pattern
     */
    private static boolean applyIncludeFilters(Path file, Path base, List<PathMatcher> matchers) {
        return matchers.isEmpty() || matchers.stream().anyMatch(m -> m.matches(base.relativize(file)));
    }

    /**
     * Applies exclude filters to a file path.
     *
     * @param file the file path
     * @param base the base directory
     * @param matchers glob matchers for exclusion
     * @return true if matchers is empty or file does not match any exclude pattern
     */
    private static boolean applyExcludeFilters(Path file, Path base, List<PathMatcher> matchers) {
        return matchers.isEmpty() || matchers.stream().noneMatch(m -> m.matches(base.relativize(file)));
    }

    /**
     * Checks whether a manifest source root is target-relative and cannot escape upward.
     *
     * @param rootEntry the manifest source root entry
     * @return true when the root entry is a safe relative path or glob
     */
    private static boolean isSafeRelativeRoot(String rootEntry) {
        final Path path = Path.of(rootEntry);
        return !rootEntry.isBlank()
                && !path.isAbsolute()
                && IntStream.range(0, path.getNameCount())
                        .noneMatch(i -> path.getName(i).toString().equals(".."));
    }
}
