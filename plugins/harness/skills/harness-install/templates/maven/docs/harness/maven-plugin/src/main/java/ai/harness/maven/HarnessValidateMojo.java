package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Maven goal that validates installed Claude repository harness assets.
 */
@Mojo(name = "validate", threadSafe = true)
public final class HarnessValidateMojo extends AbstractMojo {
    private static final String MANIFEST_PATH = "docs/harness/manifest.json";
    private static final String STACK = "maven";

    /**
     * Record to represent a validation finding with severity, category, and message.
     */
    private record Finding(String severity, String category, String message) {}

    /**
     * Interface for individual harness checks.
     */
    private interface HarnessCheck {
        String category();

        boolean applies(JsonNode manifest);

        List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException;
    }

    /**
     * Executes harness validation for the current Maven invocation root.
     */
    @Override
    public void execute() throws MojoExecutionException {
        Path root = currentRoot();
        JsonNode manifest = loadManifest(root);
        if (manifest == null) {
            return;
        }

        List<HarnessCheck> checks = List.of(
                new RequireFilesExistCheck(),
                new RequireDirectoriesExistCheck(),
                new RequireKeepfileInEmptyDirectoriesCheck(),
                new RequireTemplateGroupsCheck(),
                new RequireDocHeadingsCheck(),
                new RequireDocContentCheck(),
                new RequireAgentFrontmatterCheck(),
                new RequireSkillFrontmatterCheck(),
                new ForbidScaffoldLeaksCheck(),
                new RequireHookShebangCheck(),
                new RequireHookExecutableCheck(),
                new RequireHookGeneratedMarkerCheck(),
                new RequireHookStageCheck(),
                new RequireHookCommandCheck(),
                new RequireCiCommandMatchesHookCheck(),
                new RequireEnvShebangUnderCheck(),
                new ForbidUncheckedTasksUnderCheck(),
                new ForbidUnsafeSymlinksCheck()
        );

        Set<Finding> allFindings = new LinkedHashSet<>();
        for (HarnessCheck check : checks) {
            if (check.applies(manifest)) {
                allFindings.addAll(check.validate(root, manifest));
            }
        }

        List<Finding> sorted = allFindings.stream()
                .sorted((a, b) -> {
                    int severityOrder = severityRank(b.severity()) - severityRank(a.severity());
                    return severityOrder != 0 ? severityOrder : a.message().compareTo(b.message());
                })
                .toList();

        for (Finding finding : sorted) {
            System.err.println("[" + finding.severity() + "] " + finding.message());
        }

        long errorCount = sorted.stream().filter(f -> f.severity().equals("ERROR")).count();
        if (errorCount > 0) {
            throw new MojoExecutionException("Harness validation failed");
        }
        getLog().info("Harness validation passed");
    }

    /**
     * Returns numeric rank for severity ordering (ERROR > WARN > INFO).
     */
    private static int severityRank(String severity) {
        return switch (severity) {
            case "ERROR" -> 3;
            case "WARN" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }

    /**
     * Returns the severity from manifest for a category, or "ERROR" if not found.
     */
    private static String getSeverity(JsonNode manifest, String category) {
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
     * Returns the current working directory as an absolute Path.
     */
    private static Path currentRoot() throws MojoExecutionException {
        try {
            return Path.of(System.getProperty("user.dir")).toRealPath();
        } catch (IOException error) {
            throw new MojoExecutionException("failed to resolve current project root", error);
        }
    }

    /**
     * Loads and parses manifest.json; returns null if not found or unparseable.
     */
    private static JsonNode loadManifest(Path root) throws MojoExecutionException {
        Path manifestPath = root.resolve(MANIFEST_PATH);
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        try {
            String raw = Files.readString(manifestPath, StandardCharsets.UTF_8);
            return new ObjectMapper().readTree(raw);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads a file, following allowed symlinks.
     */
    private static String readFile(Path root, Path path) throws MojoExecutionException {
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
     * Checks if a path is a regular file (not a symlink, except allowed contracts).
     */
    private static boolean isSafeRegularFile(Path root, Path path) {
        if (Files.isSymbolicLink(path)) {
            return allowedRootContractTarget(root, path) != null;
        }
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Checks if a path is a directory (not a symlink).
     */
    private static boolean isSafeDirectory(Path root, Path path) {
        if (Files.isSymbolicLink(path)) {
            return false;
        }
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    /**
     * Lists files under a path (single file or directory recursively).
     */
    private static List<Path> safeFileOrWalk(Path root, Path base) throws MojoExecutionException {
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
     * Checks if a symlink is an allowed root contract (AGENTS.md <-> CLAUDE.md).
     */
    private static Path allowedRootContractTarget(Path root, Path path) {
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
            // ignored
        }
        return null;
    }

    /**
     * Check: requireFilesExist
     */
    private static final class RequireFilesExistCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireFilesExist";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireFilesExist");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireFilesExist");
            List<String> paths = extractPaths(catNode.get("paths"));
            String severity = getSeverity(manifest, category());
            return paths.stream()
                    .filter(path -> !isSafeRegularFile(root, root.resolve(path)))
                    .map(path -> new Finding(severity, category(), "missing file: " + path))
                    .toList();
        }
    }

    /**
     * Check: requireDirectoriesExist
     */
    private static final class RequireDirectoriesExistCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireDirectoriesExist";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireDirectoriesExist");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireDirectoriesExist");
            List<String> paths = extractPaths(catNode.get("paths"));
            String severity = getSeverity(manifest, category());
            return paths.stream()
                    .filter(path -> !isSafeDirectory(root, root.resolve(path)))
                    .map(path -> new Finding(severity, category(), "missing directory: " + path))
                    .toList();
        }
    }

    /**
     * Check: requireKeepfileInEmptyDirectories
     */
    private static final class RequireKeepfileInEmptyDirectoriesCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireKeepfileInEmptyDirectories";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireKeepfileInEmptyDirectories");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireKeepfileInEmptyDirectories");
            List<String> directories = extractPaths(catNode.get("directories"));
            String severity = getSeverity(manifest, category());
            return directories.stream()
                    .flatMap(dir -> validateKeepFile(root, dir, severity).stream())
                    .toList();
        }

        private List<Finding> validateKeepFile(Path root, String dir, String severity) {
            Path dirPath = root.resolve(dir);
            if (!isSafeDirectory(root, dirPath)) {
                return List.of();
            }
            try (Stream<Path> stream = Files.list(dirPath)) {
                List<Path> realFiles = stream
                        .filter(f -> !Files.isSymbolicLink(f) && !f.getFileName().toString().equals(".gitkeep"))
                        .toList();
                return realFiles.isEmpty() && !isSafeRegularFile(root, dirPath.resolve(".gitkeep"))
                        ? List.of(new Finding(severity, category(), "empty directory must keep placeholder or real files: " + dir))
                        : List.of();
            } catch (IOException e) {
                return List.of();
            }
        }
    }

    /**
     * Check: requireTemplateGroups
     */
    private static final class RequireTemplateGroupsCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireTemplateGroups";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireTemplateGroups");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireTemplateGroups");
            String targetRoot = catNode.get("targetRoot").asText();
            List<String> groups = extractPaths(catNode.get("groups"));
            String severity = getSeverity(manifest, category());
            return groups.stream()
                    .filter(g -> !isSafeDirectory(root, root.resolve(targetRoot).resolve(g)))
                    .map(g -> new Finding(severity, category(), "missing template group: " + targetRoot + "/" + g))
                    .toList();
        }
    }

    /**
     * Check: requireDocHeadings
     */
    private static final class RequireDocHeadingsCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireDocHeadings";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireDocHeadings") && manifest.has("requireFilesExist");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireDocHeadings");
            List<String> requiredFiles = extractPaths(manifest.get("requireFilesExist").get("paths"));
            List<String> headings = extractPaths(catNode.get("headings"));
            JsonNode sourceFilter = catNode.get("sourceFilter");
            String prefix = sourceFilter.get("prefix").asText();
            String suffix = sourceFilter.get("suffix").asText();

            String severity = getSeverity(manifest, category());

            return requiredFiles.stream()
                    .filter(f -> f.startsWith(prefix) && f.endsWith(suffix))
                    .flatMap(f -> validateHeadings(root, f, headings, severity).stream())
                    .toList();
        }

        private List<Finding> validateHeadings(Path root, String file, List<String> headings, String severity) throws MojoExecutionException {
            Path filePath = root.resolve(file);
            if (!isSafeRegularFile(root, filePath)) {
                return List.of();
            }
            String text = readFile(root, filePath);
            return headings.stream()
                    .filter(h -> !text.contains(h))
                    .map(h -> new Finding(severity, category(), "doc missing " + h + ": " + file))
                    .toList();
        }
    }

    /**
     * Check: requireDocContent
     */
    private static final class RequireDocContentCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireDocContent";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireDocContent");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireDocContent");
            JsonNode checksNode = catNode.get("checks");
            String severity = getSeverity(manifest, category());

            return Stream.of(checksNode.spliterator(), false)
                    .flatMap(check -> validateCheck(root, check, severity).stream())
                    .toList();
        }

        private List<Finding> validateCheck(Path root, JsonNode check, String severity) throws MojoExecutionException {
            List<String> files = extractPaths(check.get("files"));
            List<String> containsAll = extractPaths(check.get("containsAll"));
            String failureMessage = check.get("failureMessage").asText();

            String combined = files.stream()
                    .map(f -> {
                        try {
                            return readFile(root, root.resolve(f));
                        } catch (MojoExecutionException e) {
                            return "";
                        }
                    })
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            return containsAll.stream().allMatch(combined::contains)
                    ? List.of()
                    : List.of(new Finding(severity, category(), failureMessage));
        }
    }

    /**
     * Check: requireAgentFrontmatter
     */
    private static final class RequireAgentFrontmatterCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireAgentFrontmatter";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireAgentFrontmatter");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            Path directory = root.resolve(".claude/agents");
            String severity = getSeverity(manifest, category());

            if (!isSafeDirectory(root, directory)) {
                return List.of(new Finding(severity, category(), ".claude/agents must contain at least one .md agent"));
            }

            try (Stream<Path> stream = Files.list(directory)) {
                List<Path> files = stream
                        .filter(f -> !Files.isSymbolicLink(f) && f.getFileName().toString().endsWith(".md"))
                        .toList();

                if (files.isEmpty()) {
                    return List.of(new Finding(severity, category(), ".claude/agents must contain at least one .md agent"));
                }

                Pattern namePattern = Pattern.compile("(?m)^name:\\s*[-a-z0-9]+\\s*$");
                Pattern descPattern = Pattern.compile("(?m)^description:\\s*.+$");

                return files.stream()
                        .flatMap(f -> validateAgentFile(root, f, namePattern, descPattern, severity).stream())
                        .toList();
            }
        }

        private List<Finding> validateAgentFile(Path root, Path file, Pattern namePattern, Pattern descPattern, String severity) throws MojoExecutionException {
            String text = readFile(root, file);
            String relative = root.relativize(file).toString();
            return Stream.of(
                    text.startsWith("---") ? Stream.empty() : Stream.of(new Finding(severity, category(), "agent missing frontmatter: " + relative)),
                    namePattern.matcher(text).find() ? Stream.empty() : Stream.of(new Finding(severity, category(), "agent missing name: " + relative)),
                    descPattern.matcher(text).find() ? Stream.empty() : Stream.of(new Finding(severity, category(), "agent missing description: " + relative))
            ).flatMap(s -> s).toList();
        }
    }

    /**
     * Check: requireSkillFrontmatter
     */
    private static final class RequireSkillFrontmatterCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireSkillFrontmatter";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireSkillFrontmatter");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            Path directory = root.resolve(".claude/skills");
            String severity = getSeverity(manifest, category());

            if (!isSafeDirectory(root, directory)) {
                return List.of(new Finding(severity, category(), ".claude/skills must contain at least one SKILL.md"));
            }

            List<Path> files = safeFileOrWalk(root, directory).stream()
                    .filter(f -> f.getFileName().toString().equals("SKILL.md"))
                    .toList();

            if (files.isEmpty()) {
                return List.of(new Finding(severity, category(), ".claude/skills must contain at least one SKILL.md"));
            }

            Pattern descPattern = Pattern.compile("(?m)^description:\\s*.+$");

            return files.stream()
                    .flatMap(f -> validateSkillFile(root, f, descPattern, severity).stream())
                    .toList();
        }

        private List<Finding> validateSkillFile(Path root, Path file, Pattern descPattern, String severity) throws MojoExecutionException {
            String text = readFile(root, file);
            String relative = root.relativize(file).toString();
            return Stream.of(
                    text.startsWith("---") ? Stream.empty() : Stream.of(new Finding(severity, category(), "skill missing frontmatter: " + relative)),
                    descPattern.matcher(text).find() ? Stream.empty() : Stream.of(new Finding(severity, category(), "skill missing description: " + relative))
            ).flatMap(s -> s).toList();
        }
    }

    /**
     * Check: forbidScaffoldLeaks
     */
    private static final class ForbidScaffoldLeaksCheck implements HarnessCheck {
        @Override
        public String category() {
            return "forbidScaffoldLeaks";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("forbidScaffoldLeaks");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("forbidScaffoldLeaks");
            JsonNode scope = catNode.get("scope");
            List<String> bases = extractPaths(scope.get("bases"));
            List<String> excluded = extractPaths(scope.get("excludedSubtrees"));
            List<String> extensions = extractPaths(scope.get("extensions"));
            JsonNode patternsNode = catNode.get("patterns");
            String severity = getSeverity(manifest, category());

            List<Path> excludedPaths = excluded.stream().map(root::resolve).toList();
            String extRegex = extensions.isEmpty() ? ".*" : ".*\\.(" + String.join("|", extensions.stream().map(Pattern::quote).toList()) + ")$";
            Pattern extPattern = Pattern.compile(extRegex);

            return bases.stream()
                    .flatMap(baseName -> {
                        try {
                            Path base = root.resolve(baseName);
                            return safeFileOrWalk(root, base).stream()
                                    .filter(file -> !excludedPaths.stream().anyMatch(file::startsWith))
                                    .filter(file -> extPattern.matcher(file.toString()).matches())
                                    .flatMap(file -> checkLeaks(root, file, patternsNode, severity).stream());
                        } catch (MojoExecutionException e) {
                            return Stream.empty();
                        }
                    })
                    .toList();
        }

        private List<Finding> checkLeaks(Path root, Path file, JsonNode patternsNode, String severity) throws MojoExecutionException {
            String text = readFile(root, file);
            return Stream.of(patternsNode.spliterator(), false)
                    .filter(p -> Pattern.compile(p.get("pattern").asText()).matcher(text).find())
                    .map(p -> new Finding(severity, category(), p.get("label").asText() + " in active asset: " + root.relativize(file)))
                    .toList();
        }
    }

    /**
     * Check: requireHookShebang
     */
    private static final class RequireHookShebangCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireHookShebang";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireHookShebang");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookShebang");
            List<String> hooks = extractPaths(catNode.get("hooks"));
            String expected = catNode.get("expectedShebang").asText();
            String severity = getSeverity(manifest, category());

            return hooks.stream()
                    .flatMap(hook -> validateHookShebang(root, hook, expected, severity).stream())
                    .toList();
        }

        private List<Finding> validateHookShebang(Path root, String hook, String expected, String severity) throws MojoExecutionException {
            Path hookPath = root.resolve(hook);
            return isSafeRegularFile(root, hookPath) && !readFile(root, hookPath).startsWith(expected)
                    ? List.of(new Finding(severity, category(), hook + " must start with " + expected))
                    : List.of();
        }
    }

    /**
     * Check: requireHookExecutable
     */
    private static final class RequireHookExecutableCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireHookExecutable";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireHookExecutable");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookExecutable");
            List<String> hooks = extractPaths(catNode.get("hooks"));
            String severity = getSeverity(manifest, category());

            return hooks.stream()
                    .filter(hook -> isSafeRegularFile(root, root.resolve(hook)) && !Files.isExecutable(root.resolve(hook)))
                    .map(hook -> new Finding(severity, category(), hook + " must be executable"))
                    .toList();
        }
    }

    /**
     * Check: requireHookGeneratedMarker
     */
    private static final class RequireHookGeneratedMarkerCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireHookGeneratedMarker";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireHookGeneratedMarker");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookGeneratedMarker");
            List<String> hooks = extractPaths(catNode.get("hooks"));
            String markerTemplate = catNode.get("markerTemplate").asText();
            String placeholderForbidden = catNode.get("placeholderForbidden").asText();
            String severity = getSeverity(manifest, category());

            return hooks.stream()
                    .flatMap(hook -> validateMarker(root, hook, markerTemplate, placeholderForbidden, severity).stream())
                    .toList();
        }

        private List<Finding> validateMarker(Path root, String hook, String markerTemplate, String placeholderForbidden, String severity) throws MojoExecutionException {
            Path hookPath = root.resolve(hook);
            if (!isSafeRegularFile(root, hookPath)) {
                return List.of();
            }
            String text = readFile(root, hookPath);
            String expectedMarker = markerTemplate.replace("{name}", hookPath.getFileName().toString());
            return Stream.of(
                    text.contains(expectedMarker) ? Stream.empty() : Stream.of(new Finding(severity, category(), hook + " must contain generated marker '" + expectedMarker + "'")),
                    text.contains(placeholderForbidden) ? Stream.of(new Finding(severity, category(), hook + " still contains packaging placeholder text")) : Stream.empty()
            ).flatMap(s -> s).toList();
        }
    }

    /**
     * Check: requireHookStage
     */
    private static final class RequireHookStageCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireHookStage";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireHookStage");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookStage");
            JsonNode stages = catNode.get("stages");
            JsonNode stackStages = stages.get(STACK);
            if (stackStages == null) {
                return List.of();
            }

            String markerTemplate = catNode.get("markerTemplate").asText();
            String severity = getSeverity(manifest, category());

            return Stream.of(stackStages.fieldNames().spliterator(), false)
                    .flatMap(hookName -> validateStage(root, hookName, stackStages.get(hookName).asText(), markerTemplate, severity).stream())
                    .toList();
        }

        private List<Finding> validateStage(Path root, String hookName, String expectedStage, String markerTemplate, String severity) throws MojoExecutionException {
            String hookPath = "docs/harness/git-hooks/" + hookName;
            Path hook = root.resolve(hookPath);
            if (!isSafeRegularFile(root, hook)) {
                return List.of();
            }
            String text = readFile(root, hook);
            String expectedMarker = markerTemplate.replace("{stage}", expectedStage);
            return text.contains(expectedMarker)
                    ? List.of()
                    : List.of(new Finding(severity, category(), hookPath + " must contain stage marker '" + expectedMarker + "'"));
        }
    }

    /**
     * Check: requireHookCommand
     */
    private static final class RequireHookCommandCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireHookCommand";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireHookCommand");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookCommand");
            JsonNode allowedCmds = catNode.get("allowedCommands").get(STACK);
            String expectedCmd = allowedCmds == null || allowedCmds.size() == 0 ? "" : allowedCmds.get(0).asText();
            String severity = getSeverity(manifest, category());

            String prePushPath = catNode.get("prePushHook").asText();
            Path prePush = root.resolve(prePushPath);
            if (!isSafeRegularFile(root, prePush)) {
                return List.of();
            }

            String prePushText = readFile(root, prePush);
            String declaredCmd = extractHookCommand(prePushText);

            List<Finding> prePushIssues = declaredCmd.isEmpty()
                    ? List.of(new Finding(severity, category(), "pre-push hook must declare Harness validation command"))
                    : !expectedCmd.equals(declaredCmd)
                    ? List.of(new Finding(severity, category(), "pre-push hook declares unsupported validation command: " + declaredCmd))
                    : !prePushText.contains(declaredCmd)
                    ? List.of(new Finding(severity, category(), "pre-push hook must run the declared validation command"))
                    : List.of();

            String preCommitPath = catNode.get("preCommitHook").asText();
            Path preCommit = root.resolve(preCommitPath);
            if (!isSafeRegularFile(root, preCommit)) {
                return prePushIssues;
            }

            String preCommitText = readFile(root, preCommit);
            Pattern fullStackPattern = Pattern.compile("(^|\\s)(uv|bun|gradle|mvn)(\\s|$)|\\.\\./gradlew|harnessValidate|harness_validate\\.py|harness-validate\\.ts");
            List<Finding> preCommitIssues = fullStackPattern.matcher(preCommitText).find()
                    ? List.of(new Finding(severity, category(), "pre-commit hook must not run full stack validation commands"))
                    : List.of();

            return Stream.concat(prePushIssues.stream(), preCommitIssues.stream()).toList();
        }

        private String extractHookCommand(String text) {
            for (String line : text.split("\\R")) {
                if (line.startsWith("# Harness validation command: ")) {
                    return line.substring("# Harness validation command: ".length()).trim();
                }
            }
            return "";
        }
    }

    /**
     * Check: requireCiCommandMatchesHook
     */
    private static final class RequireCiCommandMatchesHookCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireCiCommandMatchesHook";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireCiCommandMatchesHook");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireCiCommandMatchesHook");
            String refHook = catNode.get("referenceHook").asText();
            Path refPath = root.resolve(refHook);

            if (!isSafeRegularFile(root, refPath)) {
                return List.of();
            }

            String refText = readFile(root, refPath);
            String expectedCmd = extractHookCommand(refText);
            if (expectedCmd.isEmpty()) {
                return List.of();
            }

            List<String> ciFiles = extractPaths(catNode.get("ciFiles"));
            String severity = getSeverity(manifest, category());
            final String cmd = expectedCmd;

            return ciFiles.stream()
                    .flatMap(ciFile -> validateCiFile(root, ciFile, cmd, severity).stream())
                    .toList();
        }

        private List<Finding> validateCiFile(Path root, String ciFile, String expectedCmd, String severity) throws MojoExecutionException {
            Path ciPath = root.resolve(ciFile);
            if (!Files.exists(ciPath, LinkOption.NOFOLLOW_LINKS) || !isSafeRegularFile(root, ciPath)) {
                return List.of();
            }
            String ciText = readFile(root, ciPath);
            return ciText.contains(expectedCmd)
                    ? List.of()
                    : List.of(new Finding(severity, category(), ciFile + ": CI command mismatch — expected " + expectedCmd));
        }

        private String extractHookCommand(String text) {
            for (String line : text.split("\\R")) {
                if (line.startsWith("# Harness validation command: ")) {
                    return line.substring("# Harness validation command: ".length()).trim();
                }
            }
            return "";
        }
    }

    /**
     * Check: requireEnvShebangUnder
     */
    private static final class RequireEnvShebangUnderCheck implements HarnessCheck {
        @Override
        public String category() {
            return "requireEnvShebangUnder";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("requireEnvShebangUnder");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireEnvShebangUnder");
            List<String> directories = extractPaths(catNode.get("directories"));
            String expectedPrefix = catNode.get("expectedPrefix").asText();
            String severity = getSeverity(manifest, category());

            return directories.stream()
                    .flatMap(dir -> {
                        try {
                            Path dirPath = root.resolve(dir);
                            return safeFileOrWalk(root, dirPath).stream()
                                    .filter(Files::isExecutable)
                                    .flatMap(file -> validateShebang(root, file, expectedPrefix, severity).stream());
                        } catch (MojoExecutionException e) {
                            return Stream.empty();
                        }
                    })
                    .toList();
        }

        private List<Finding> validateShebang(Path root, Path file, String expectedPrefix, String severity) throws MojoExecutionException {
            String text = readFile(root, file);
            return text.startsWith("#!") && !text.startsWith(expectedPrefix)
                    ? List.of(new Finding(severity, category(), "executable script should use /usr/bin/env shebang: " + root.relativize(file)))
                    : List.of();
        }
    }

    /**
     * Check: forbidUncheckedTasksUnder
     */
    private static final class ForbidUncheckedTasksUnderCheck implements HarnessCheck {
        @Override
        public String category() {
            return "forbidUncheckedTasksUnder";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("forbidUncheckedTasksUnder");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("forbidUncheckedTasksUnder");
            String directory = catNode.get("directory").asText();
            String uncheckedPattern = catNode.get("uncheckedTaskPattern").asText();
            String severity = getSeverity(manifest, category());

            Path dirPath = root.resolve(directory);
            if (!isSafeDirectory(root, dirPath)) {
                return List.of();
            }

            List<Path> files = safeFileOrWalk(root, dirPath).stream()
                    .filter(f -> f.getFileName().toString().endsWith(".md") && !f.getFileName().toString().equals(".gitkeep"))
                    .toList();

            Pattern pattern = Pattern.compile(uncheckedPattern);

            return files.stream()
                    .flatMap(file -> {
                        try {
                            String text = readFile(root, file);
                            return pattern.matcher(text).find()
                                    ? Stream.of(new Finding(severity, category(), "completed plan has unchecked tasks: " + root.relativize(file)))
                                    : Stream.empty();
                        } catch (MojoExecutionException e) {
                            return Stream.empty();
                        }
                    })
                    .toList();
        }
    }

    /**
     * Check: forbidUnsafeSymlinks
     */
    private static final class ForbidUnsafeSymlinksCheck implements HarnessCheck {
        @Override
        public String category() {
            return "forbidUnsafeSymlinks";
        }

        @Override
        public boolean applies(JsonNode manifest) {
            return manifest.has("forbidUnsafeSymlinks");
        }

        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("forbidUnsafeSymlinks");
            JsonNode allowedNode = catNode.get("allowedSymlinkPairs");
            Set<String> allowedNames = new LinkedHashSet<>();

            for (JsonNode pair : allowedNode) {
                allowedNames.add(pair.get(0).asText());
                allowedNames.add(pair.get(1).asText());
            }

            String severity = getSeverity(manifest, category());

            List<String> rootBases = List.of("AGENTS.md", "CLAUDE.md", "ARCHITECTURE.md", "docs", ".claude", ".github");
            return rootBases.stream()
                    .flatMap(baseName -> {
                        try {
                            Path base = root.resolve(baseName);
                            return validateSymlinks(root, base, allowedNames, severity).stream();
                        } catch (MojoExecutionException e) {
                            return Stream.empty();
                        }
                    })
                    .toList();
        }

        private List<Finding> validateSymlinks(Path root, Path base, Set<String> allowedNames, String severity) throws MojoExecutionException {
            if (Files.isSymbolicLink(base)) {
                String name = base.getFileName().toString();
                return !allowedNames.contains(name) || allowedRootContractTarget(root, base) == null
                        ? List.of(new Finding(severity, category(), "symlink scan root is not allowed: " + root.relativize(base)))
                        : List.of();
            }

            List<Path> files = safeFileOrWalk(root, base);
            return files.stream()
                    .filter(Files::isSymbolicLink)
                    .flatMap(file -> {
                        String name = file.getFileName().toString();
                        return !allowedNames.contains(name) || allowedRootContractTarget(root, file) == null
                                ? Stream.of(new Finding(severity, category(), "symlink " + (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) ? "directory" : "file") + " is not allowed: " + root.relativize(file)))
                                : Stream.empty();
                    })
                    .toList();
        }
    }

    /**
     * Extracts a list of strings from a JsonNode array.
     */
    private static List<String> extractPaths(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return Stream.of(node.spliterator(), false).map(JsonNode::asText).toList();
    }
}
