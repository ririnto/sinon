package com.ririnto.sinon.harness.rules;


import com.ririnto.sinon.harness.Finding;
import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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
        if (!isContainedUnderRoot(root, path)) {
            throw new MojoExecutionException("path escapes project root: " + path);
        }
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
        if (!isContainedUnderRoot(root, path)) {
            return false;
        }
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
        if (!isContainedUnderRoot(root, path)) {
            return false;
        }
        return !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Lists safe regular files in a directory or returns a single file if path is a file.
     * Symlinks are excluded so that formatting and parsing rules cannot follow
     * symlink targets outside the project root.
     *
     * @param root the project root directory
     * @param base the base path (file or directory)
     * @return list of safe regular files
     * @throws MojoExecutionException if inspection fails
     */
    public static List<Path> safeFileOrWalk(Path root, Path base) throws MojoExecutionException {
        if (!isContainedUnderRoot(root, base) || Files.isSymbolicLink(base)) {
            return List.of();
        }
        if (isSafeRegularFile(root, base)) {
            return List.of(base);
        }
        if (!isSafeDirectory(root, base)) {
            return List.of();
        }

        final List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return isWorktreePath(root, dir) || !isSafeDirectory(root, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (!path.equals(base) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                        files.add(path);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    throw exc;
                }
            });
        } catch (IOException error) {
            throw new MojoExecutionException("failed to inspect " + base, error);
        }
        return files;
    }

    /**
     * Result of a symlink safety scan.
     */
    public record SymlinkScanResult(List<Path> symlinks, List<Finding> findings) {
    }

    /**
     * Collects symlink paths and scan-time findings for a scan root.
     * Returns symlink paths so that rules such as SymlinkSafetyRule can classify them.
     * The findings are safe for callers that need to continue reporting
     * discoveries when one path is unreadable.
     *
     * @param root the project root directory
     * @param base the base path (file or directory)
     * @return symlink paths and inspection findings
     */
    public static SymlinkScanResult symlinkScanResult(Path root, Path base) {
        if (!isContainedUnderRoot(root, base)) {
            return new SymlinkScanResult(List.of(), List.of());
        }
        if (Files.isSymbolicLink(base)) {
            return new SymlinkScanResult(List.of(base), List.of());
        }
        if (isSafeRegularFile(root, base)) {
            return new SymlinkScanResult(List.of(), List.of());
        }
        if (!isSafeDirectory(root, base)) {
            return new SymlinkScanResult(List.of(), List.of());
        }

        final List<Path> symlinks = new ArrayList<>();
        final List<Finding> findings = new ArrayList<>();
        try {
            Files.walkFileTree(base, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return isWorktreePath(root, dir) || !isSafeDirectory(root, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (!path.equals(base) && Files.isSymbolicLink(path)) {
                        symlinks.add(path);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    findings.add(Finding.of("ERROR", "symlinkSafety", "failed to inspect symlinks under " + base + ": " + exc.getMessage()));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException error) {
            findings.add(Finding.of("ERROR", "symlinkSafety", "failed to inspect symlinks under " + base + ": " + error.getMessage()));
        }
        return new SymlinkScanResult(symlinks, findings);
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
     * Collects source-root safety findings for manifest categories and roots.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @return list of findings for unsafe source-root entries
     * @throws IOException if manifest processing fails
     */
    public static List<Finding> stackSourceFindings(JsonNode manifest, String category) throws IOException {
        return stackSourceFindings(manifest, category, "java", Path.of("."));
    }

    /**
     * Collects source-root safety findings for manifest categories and roots.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @param stack retained for signature compatibility; ignored under flat parameter shape
     * @return list of findings for unsafe source-root entries
     * @throws IOException if manifest processing fails
     */
    public static List<Finding> stackSourceFindings(JsonNode manifest, String category, String stack) throws IOException {
        return stackSourceFindings(manifest, category, stack, Path.of("."));
    }

    /**
     * Collects source-root safety findings for manifest categories and roots.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @param stack retained for signature compatibility; ignored under flat parameter shape
     * @param projectRoot the project root for relative path resolution
     * @return list of findings for unsafe source-root entries
     * @throws IOException if manifest processing fails
     */
    public static List<Finding> stackSourceFindings(JsonNode manifest, String category, String stack, Path projectRoot) throws IOException {
        final List<Finding> rootFindings = collectRootSafetyFindings(manifest, category, projectRoot);
        final StackSourceResult result = collectStackSourcesWithFindings(manifest, category, stack, projectRoot);
        return Stream.concat(rootFindings.stream(), result.findings().stream())
                .distinct()
                .toList();
    }

    /**
     * Collects source-root safety findings for manifest categories and roots.
     */
    private static List<Finding> collectRootSafetyFindings(JsonNode manifest, String category, Path projectRoot) throws IOException {
        final JsonNode categoryNode = manifest.get(category);
        final JsonNode params = categoryNode == null ? null : categoryNode.get("parameters");
        final JsonNode rootsNode = params == null ? null : params.get("sourceRoots");
        final JsonNode extsNode = params == null ? null : params.get("extensions");
        if (rootsNode == null || extsNode == null) {
            return List.of();
        }
        final FileSystem fs = FileSystems.getDefault();
        final Path base = projectRoot;
        final Path baseReal = base.toRealPath();
        final List<Finding> findings = new ArrayList<>(extractPaths(rootsNode).size());
        for (final String rootEntry : extractPaths(rootsNode)) {
            if (rootEntry.isBlank()) {
                continue;
            }
            final Path rootPath = Path.of(rootEntry);
            if (rootPath.isAbsolute()) {
                findings.add(Finding.of("ERROR", "symlinkSafety", "absolute source root is not allowed: " + rootEntry));
                continue;
            }
            if (hasRelativeTraversal(rootPath)) {
                findings.add(Finding.of("ERROR", "symlinkSafety", "source root traversal is not allowed: " + rootEntry));
                continue;
            }
            final boolean hasGlob = hasGlobTokens(rootEntry);
            if (hasGlob) {
                try {
                    fs.getPathMatcher("glob:" + rootEntry);
                } catch (IllegalArgumentException ignored) {
                    findings.add(Finding.of("ERROR", "symlinkSafety", "invalid glob source root pattern: " + rootEntry));
                }
                continue;
            }
            final Path candidateRoot = base.resolve(rootPath).normalize();
            if (!Files.exists(candidateRoot, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!isRootContainedDirectory(base, baseReal, candidateRoot)) {
                findings.add(Finding.of("ERROR", "symlinkSafety", "source root is not contained or is a symlink: " + rootEntry));
            }
        }
        return findings.stream().distinct().toList();
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
     * Stack source result with collected findings (glob errors and parse errors).
     */
    public record StackSourceResult(List<Path> paths, List<Finding> findings) {
    }

    /**
     * Collects all source files for a category, collecting glob and parse-error findings.
     *
     * @param manifest the manifest JSON node
     * @param category the category name
     * @param stack the stack identifier
     * @param projectRoot the project root for relative path resolution
     * @return stack source result with paths and findings
     */
    public static StackSourceResult collectStackSourcesWithFindings(JsonNode manifest, String category, String stack, Path projectRoot) {
        final JsonNode categoryNode = manifest.get(category);
        final JsonNode params = categoryNode == null ? null : categoryNode.get("parameters");
        final JsonNode rootsNode = params == null ? null : params.get("sourceRoots");
        final JsonNode extsNode = params == null ? null : params.get("extensions");
        if (rootsNode == null || extsNode == null) {
            return new StackSourceResult(Collections.emptyList(), List.of());
        }
        final Set<String> extensions = StreamSupport.stream(extsNode.spliterator(), false)
                .map(JsonNode::asString)
                .collect(Collectors.toSet());
        final List<String> includes = extractPaths(params.get("includePaths"));
        final List<String> excludes = extractPaths(params.get("excludePaths"));
        final FileSystem fs = FileSystems.getDefault();
        final List<Path> results = new ArrayList<>(extractPaths(rootsNode).size());
        final List<Finding> globFindings = new ArrayList<>();
        for (final String rootEntry : extractPaths(rootsNode)) {
            try {
                results.addAll(walkRoot(fs, projectRoot, rootEntry, extensions, includes, excludes, globFindings));
            } catch (IOException e) {
                globFindings.add(Finding.of("ERROR", "symlinkSafety", "failed to walk source root: " + rootEntry));
            }
        }
        final ParseSourceResult parseResult = collectParseErrorFindings(results.stream().distinct().toList());
        return new StackSourceResult(
                parseResult.parseableSources().stream().distinct().sorted().toList(),
                Stream.concat(globFindings.stream(), parseResult.parseErrorFindings().stream()).distinct().toList());
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
        return collectStackSourcesWithFindings(manifest, category, stack, projectRoot).paths();
    }

    private static boolean hasRelativeTraversal(Path path) {
        return IntStream.range(0, path.getNameCount())
                .anyMatch(i -> path.getName(i).toString().equals(".."));
    }

    private static boolean hasGlobTokens(String value) {
        return value.contains("*") || value.contains("?") || value.contains("[") || value.contains("{");
    }

    /**
     * Walks a root directory matching file extensions, with include/exclude filters.
     * Malformed include/exclude glob patterns emit ERROR findings and are skipped.
     *
     * @param fs the file system
     * @param base the base path
     * @param rootEntry the root entry (may contain wildcards)
     * @param extensions the set of allowed extensions
     * @param includes glob patterns for inclusion (empty = match all)
     * @param excludes glob patterns for exclusion (empty = exclude none)
     * @param globFindings accumulator for malformed glob findings
     * @return list of matching files
     * @throws IOException if directory walk fails
     */
    public static List<Path> walkRoot(FileSystem fs, Path base, String rootEntry, Set<String> extensions, List<String> includes, List<String> excludes, List<Finding> globFindings) throws IOException {
        if (!isSafeRelativeRoot(rootEntry)) {
            return Collections.emptyList();
        }
        final boolean hasGlob = hasGlobTokens(rootEntry);
        final List<PathMatcher> includeMatchers = compileGlobMatchers(fs, includes, "includePaths", globFindings);
        final List<PathMatcher> excludeMatchers = compileGlobMatchers(fs, excludes, "excludePaths", globFindings);
        final Path rootPath = base.resolve(rootEntry).normalize();
        final Path baseReal = base.toRealPath();
        if (!hasGlob && !isRootContainedDirectory(base, baseReal, rootPath)) {
            return List.of();
        }
        final PathMatcher rootMatcher;
        if (hasGlob) {
            try {
                rootMatcher = fs.getPathMatcher("glob:" + rootEntry + "/**/*");
            } catch (IllegalArgumentException ignored) {
                return List.of();
            }
        } else {
            rootMatcher = null;
        }

        final List<Path> files = new ArrayList<>();
        final Path start = hasGlob ? base : rootPath;
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return isWorktreePath(base, dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                if ((rootMatcher == null || rootMatcher.matches(base.relativize(path))) && isSourceRootMatch(base, baseReal, path, extensions, includeMatchers, excludeMatchers)) {
                    files.add(path.toAbsolutePath().normalize());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }
        });
        return files;
    }

    private static boolean isSourceRootMatch(Path base, Path baseReal, Path path, Set<String> extensions, List<PathMatcher> includeMatchers, List<PathMatcher> excludeMatchers) {
        return isRootContainedRegularFile(base, baseReal, path)
                && !isWorktreePath(base, path)
                && extensions.contains(extensionOf(path))
                && !containsSegment(base.relativize(path), "target")
                && !containsSegment(base.relativize(path), "build")
                && applyIncludeFilters(path, base, includeMatchers)
                && applyExcludeFilters(path, base, excludeMatchers);
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
     * Checks whether a path is a root-contained regular file without symlink components.
     *
     * @param root the project root
     * @param rootReal the resolved project root
     * @param path the candidate file
     * @return true when the path is contained under root and is not reached through symlinks
     */
    public static boolean isRootContainedRegularFile(Path root, Path rootReal, Path path) {
        try {
            final Path realPath = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
            return realPath.startsWith(rootReal)
                    && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !hasSymlinkSegment(root, path);
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Checks whether a path is a root-contained directory without symlink components.
     *
     * @param root the project root
     * @param rootReal the resolved project root
     * @param path the candidate directory
     * @return true when the path is contained under root and is not reached through symlinks
     */
    public static boolean isRootContainedDirectory(Path root, Path rootReal, Path path) {
        try {
            final Path realPath = path.toRealPath(LinkOption.NOFOLLOW_LINKS);
            return realPath.startsWith(rootReal)
                    && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    && !hasSymlinkSegment(root, path);
        } catch (IOException | IllegalArgumentException e) {
            return false;
        }
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
     * Checks whether a path contains a symlink at any segment below the root.
     *
     * @param root the project root
     * @param path the candidate path
     * @return true when any segment is a symlink or path cannot be relativized
     */
    private static boolean hasSymlinkSegment(Path root, Path path) {
        try {
            final Path relative = root.relativize(path);
            Path current = root;
            for (final Path segment : relative) {
                final String segmentName = segment.toString();
                if (".".equals(segmentName)) {
                    continue;
                }
                if ("..".equals(segmentName)) {
                    return true;
                }
                current = current.resolve(segment);
                if (Files.isSymbolicLink(current)) {
                    return true;
                }
            }
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
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
     * Attempts to parse each source file and collects parse-error findings.
     * Files that fail to parse are identified so AST rules can skip them.
     *
     * @param sources the source file paths
     * @return list of parse-error findings
     */
    private record ParseSourceResult(List<Path> parseableSources, List<Finding> parseErrorFindings) {
    }

    static ParseSourceResult collectParseErrorFindings(List<Path> sources) {
        final List<Path> parseableSources = new ArrayList<>(sources.size());
        final List<Finding> findings = new ArrayList<>();
        for (final Path source : sources) {
            try {
                com.github.javaparser.StaticJavaParser.parse(source);
                parseableSources.add(source);
            } catch (IOException | com.github.javaparser.ParseProblemException e) {
                findings.add(Finding.of("ERROR", "parseError", "java parse error in " + source.getFileName() + ": " + e.getMessage()));
            }
        }
        return new ParseSourceResult(parseableSources.stream().distinct().toList(), findings.stream().distinct().toList());
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
     * Compiles glob matchers from pattern strings, collecting malformed patterns as ERROR findings.
     *
     * @param fs the file system
     * @param patterns glob pattern strings
     * @param label parameter label for finding messages
     * @param findings accumulator for malformed glob findings
     * @return list of compiled path matchers
     */
    static List<PathMatcher> compileGlobMatchers(FileSystem fs, List<String> patterns, String label, List<Finding> findings) {
        final List<PathMatcher> matchers = new ArrayList<>(patterns.size());
        for (final String pattern : patterns) {
            try {
                matchers.add(fs.getPathMatcher("glob:" + pattern));
            } catch (IllegalArgumentException e) {
                findings.add(Finding.of("ERROR", "symlinkSafety", "invalid " + label + " glob pattern: " + pattern));
            }
        }
        return matchers;
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
    /**
     * Checks whether a path is contained under the given root (does not escape upward).
     * Absolute paths are rejected unless they start with the root.
     *
     * @param root the project root directory
     * @param path the candidate path
     * @return true when the path is contained under root
     */
    static boolean isContainedUnderRoot(Path root, Path path) {
        if (path.isAbsolute()) {
            final Path normalizedRoot = root.normalize();
            final Path normalizedPath = path.normalize();
            return normalizedPath.startsWith(normalizedRoot);
        }
        final Path resolved = root.resolve(path).normalize();
        return resolved.startsWith(root.normalize());
    }

    /**
     * Checks whether a path or any ancestor segment contains a worktree directory
     * that should be excluded from source enumeration.
     *
     * @param base the base directory
     * @param path the candidate path (relative to base)
     * @return true when the path is under .claude/worktrees
     */
    static boolean isWorktreePath(Path base, Path path) {
        try {
            final Path relative = base.relativize(path).normalize();
            return containsSegment(relative, ".claude")
                    && IntStream.range(0, relative.getNameCount()).anyMatch(i -> {
                        if (relative.getName(i).toString().equals(".claude")) {
                            return i + 1 < relative.getNameCount() && relative.getName(i + 1).toString().equals("worktrees");
                        }
                        return false;
                    });
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
