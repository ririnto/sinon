package ai.harness.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Maven goal that validates installed Claude repository harness assets. */
@Mojo(name = "validate", threadSafe = true)
public final class HarnessValidateMojo extends AbstractMojo {
    private static final String MANIFEST_PATH = "docs/harness/manifest.json";
    private static final String STACK_IDENTIFIER = "maven";

    /** Record to represent a validation finding with severity, category, and message. */
    private record Finding(String severity, String category, String message) {}

    /** Executes harness validation for the current Maven invocation root. */
    @Override
    public void execute() throws MojoExecutionException {
        Path root = currentRoot();
        ManifestConfig config = loadManifestOrDefault(root);

        List<Finding> allFindings =
                Stream.of(
                                new ManifestValidator().validate(root),
                                new StructureValidator().validate(root),
                                new ContentValidator().validate(root),
                                new AgentsValidator().validate(root),
                                new SkillsValidator().validate(root),
                                new TemplateValidator().validate(root),
                                new ActiveAssetsValidator().validate(root),
                                new HooksValidator().validate(root),
                                new ShebangValidator().validate(root),
                                new PlanCompletionValidator().validate(root))
                        .flatMap(List::stream)
                        .distinct()
                        .sorted((a, b) -> {
                            int severityOrder =
                                    severityRank(b.severity()) - severityRank(a.severity());
                            return severityOrder != 0
                                    ? severityOrder
                                    : a.message().compareTo(b.message());
                        })
                        .toList();

        for (Finding finding : allFindings) {
            System.err.println("[" + finding.severity() + "] " + finding.message());
        }

        long errorCount =
                allFindings.stream().filter(f -> f.severity().equals("ERROR")).count();
        if (errorCount > 0) {
            throw new MojoExecutionException("Harness validation failed");
        }
        getLog().info("Harness validation passed");
    }

    /** Returns numeric rank for severity ordering (ERROR > WARN > INFO). */
    private static int severityRank(String severity) {
        return switch (severity) {
            case "ERROR" -> 3;
            case "WARN" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }

    private static Path currentRoot() throws MojoExecutionException {
        try {
            return Path.of(System.getProperty("user.dir")).toRealPath();
        } catch (IOException error) {
            throw new MojoExecutionException("failed to resolve current project root", error);
        }
    }

    /** Loads manifest.json and provides parsed configuration. */
    private static final class ManifestConfig {
        private final String raw;
        private final List<String> requiredFiles;
        private final List<String> requiredDirectories;
        private final List<String> emptyDirectoryKeepFiles;
        private final List<String> optionalSeedFiles;
        private final List<String> templateGroups;
        private final List<String> requiredDocHeadings;
        private final List<RequiredContentCheck> requiredContentChecks;
        private final List<String> activeAssetBases;
        private final List<String> excludedActiveAssetSubtrees;
        private final List<String> activeAssetExtensions;
        private final List<LeakPattern> leakPatterns;
        private final String expectedValidationCommand;
        private final String hookPreCommitStage;
        private final String hookPrePushStage;
        private final String completedPlanDirectory;
        private final String unfinishedTaskPattern;
        private final List<String> envShebangBases;

        ManifestConfig(String raw) {
            this.raw = raw;
            this.requiredFiles = extractWrappedStringList("requiredFiles");
            this.requiredDirectories = extractWrappedStringList("requiredDirectories");
            this.emptyDirectoryKeepFiles = extractWrappedStringList("emptyDirectoryKeepFiles");
            this.optionalSeedFiles = extractWrappedStringList("optionalSeedFiles");
            this.templateGroups = extractWrappedStringList("templateGroups");
            this.requiredDocHeadings = extractWrappedStringList("requiredDocHeadings");
            this.requiredContentChecks = extractContentChecks();
            this.activeAssetBases = extractActiveAssetBases();
            this.excludedActiveAssetSubtrees = extractActiveAssetExcludedSubtrees();
            this.activeAssetExtensions = extractActiveAssetExtensions();
            this.leakPatterns = extractLeakPatterns();
            this.expectedValidationCommand = extractExpectedCommand();
            this.hookPreCommitStage = extractHookStage("preCommit");
            this.hookPrePushStage = extractHookStage("prePush");
            this.completedPlanDirectory = extractWrappedString("completedPlanDirectory");
            this.unfinishedTaskPattern = extractWrappedString("unfinishedTaskPattern");
            this.envShebangBases = extractWrappedStringList("envShebangBases");
        }

        private List<String> extractWrappedStringList(String key) {
            Pattern pattern =
                    Pattern.compile(
                            "\\\"" + key + "\\\"\\s*:\\s*\\{[^}]*\\\"items\\\"\\s*:\\s*\\[(.*?)\\]",
                            Pattern.DOTALL);
            Matcher matcher = pattern.matcher(raw);
            return matcher.find()
                    ? Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(matcher.group(1))
                            .results()
                            .map(m -> m.group(1))
                            .toList()
                    : List.of();
        }

        private List<String> extractActiveAssetBases() {
            Pattern pattern =
                    Pattern.compile(
                            "\\\"activeAssets\\\"\\s*:\\s*\\{[^}]*\\\"bases\\\"\\s*:\\s*\\[(.*?)\\]",
                            Pattern.DOTALL);
            Matcher matcher = pattern.matcher(raw);
            return matcher.find()
                    ? Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(matcher.group(1))
                            .results()
                            .map(m -> m.group(1))
                            .toList()
                    : List.of();
        }

        private List<String> extractActiveAssetExcludedSubtrees() {
            Pattern pattern =
                    Pattern.compile(
                            "\\\"activeAssets\\\"\\s*:\\s*\\{[^}]*\\\"excludedSubtrees\\\"\\s*:\\s*\\[(.*?)\\]",
                            Pattern.DOTALL);
            Matcher matcher = pattern.matcher(raw);
            return matcher.find()
                    ? Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(matcher.group(1))
                            .results()
                            .map(m -> m.group(1))
                            .toList()
                    : List.of();
        }

        private List<String> extractActiveAssetExtensions() {
            Pattern pattern =
                    Pattern.compile(
                            "\\\"activeAssets\\\"\\s*:\\s*\\{[^}]*\\\"extensions\\\"\\s*:\\s*\\[(.*?)\\]",
                            Pattern.DOTALL);
            Matcher matcher = pattern.matcher(raw);
            return matcher.find()
                    ? Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(matcher.group(1))
                            .results()
                            .map(m -> m.group(1))
                            .toList()
                    : List.of();
        }

        private String extractWrappedString(String key) {
            Pattern pattern =
                    Pattern.compile(
                            "\\\"" + key + "\\\"\\s*:\\s*\\{[^}]*\\\"value\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"",
                            Pattern.DOTALL);
            Matcher matcher = pattern.matcher(raw);
            return matcher.find() ? unescapeJson(matcher.group(1)) : "";
        }

        private List<RequiredContentCheck> extractContentChecks() {
            Pattern pattern = Pattern.compile(
                    "\"requiredContentChecks\"\\s*:\\s*\\{[^}]*\"items\"\\s*:\\s*\\[(.*?)\\](?=,|\\})",
                    Pattern.DOTALL);
            Matcher matcher = pattern.matcher(raw);
            if (!matcher.find()) {
                return List.of();
            }

            String checksBody = matcher.group(1);
            Pattern checkPattern = Pattern.compile(
                    "\\{\\s*\"files\"\\s*:\\s*\\[(.*?)\\]\\s*,\\s*"
                            + "\"containsAll\"\\s*:\\s*\\[(.*?)\\]\\s*,\\s*"
                            + "\"failureMessage\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL);
            return checkPattern.matcher(checksBody).results()
                    .map(m -> new RequiredContentCheck(
                            extractListFromQuotes(m.group(1)),
                            extractListFromQuotes(m.group(2)),
                            m.group(3)))
                    .toList();
        }

        private List<LeakPattern> extractLeakPatterns() {
            Pattern pattern = Pattern.compile(
                    "\"leakPatterns\"\\s*:\\s*\\{[^}]*\"items\"\\s*:\\s*\\[(.*?)\\](?=,|\\})",
                    Pattern.DOTALL);
            Matcher matcher = pattern.matcher(raw);
            if (!matcher.find()) {
                return List.of();
            }

            String patternsBody = matcher.group(1);
            Pattern patternPattern = Pattern.compile(
                    "\\{\\s*\"pattern\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*"
                            + "\"label\"\\s*:\\s*\"([^\"]+)\"",
                    Pattern.DOTALL);
            return patternPattern.matcher(patternsBody).results()
                    .map(m -> new LeakPattern(
                            Pattern.compile(unescapeJson(m.group(1))),
                            m.group(2)))
                    .toList();
        }

        private List<String> extractListFromQuotes(String input) {
            Pattern quoted = Pattern.compile("\\\"([^\\\"]+)\\\"");
            return quoted.matcher(input).results()
                    .map(m -> m.group(1))
                    .toList();
        }

        private String extractExpectedCommand() {
            Pattern pattern = Pattern.compile(
                    "\"expectedValidationCommands\"\\s*:\\s*\\{[^}]*\"" + STACK_IDENTIFIER
                            + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(raw);
            return matcher.find() ? unescapeJson(matcher.group(1)) : "";
        }

        private String extractHookStage(String stage) {
            Pattern pattern = Pattern.compile(
                    "\"hookStages\"\\s*:\\s*\\{[^}]*\"" + STACK_IDENTIFIER
                            + "\"\\s*:\\s*\\{[^}]*\"" + stage
                            + "\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(raw);
            return matcher.find() ? matcher.group(1) : "";
        }

        private String unescapeJson(String input) {
            return input.replace("\\\\", "\\")
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
        }

        String getSeverity(String category) {
            Pattern pattern =
                    Pattern.compile(
                            "\\\"" + category + "\\\"\\s*:\\s*\\{[^}]*\\\"severity\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
                            Pattern.DOTALL);
            Matcher matcher = pattern.matcher(raw);
            return matcher.find() ? matcher.group(1) : "ERROR";
        }
    }

    /** Structure to hold required content check data. */
    private static final class RequiredContentCheck {
        private final List<String> files;
        private final List<String> containsAll;
        private final String failureMessage;

        RequiredContentCheck(List<String> files, List<String> containsAll, String failureMessage) {
            this.files = List.copyOf(files);
            this.containsAll = List.copyOf(containsAll);
            this.failureMessage = failureMessage;
        }

        List<String> files() {
            return files;
        }

        List<String> containsAll() {
            return containsAll;
        }

        String failureMessage() {
            return failureMessage;
        }
    }

    /** Validates manifest.json is readable and valid JSON. */
    private static final class ManifestValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            Path manifestPath = root.resolve(MANIFEST_PATH);
            if (Files.isSymbolicLink(manifestPath)) {
                return List.of(
                        new Finding("ERROR", "symlinkSafety",
                                "symlink file is not allowed: " + MANIFEST_PATH));
            }
            String manifest = HarnessFiles.read(manifestPath);
            if (manifest.isBlank()) {
                return List.of(
                        new Finding("ERROR", "requiredFiles",
                                "missing file: " + MANIFEST_PATH));
            }

            if (!manifest.trim().startsWith("{")) {
                return List.of(
                        new Finding("ERROR", "requiredFiles",
                                "invalid JSON in: " + MANIFEST_PATH));
            }

            return List.of();
        }
    }

    /** Validates file and directory structure. */
    private static final class StructureValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            ManifestConfig config = loadManifestOrDefault(root);
            if (config == null) {
                return List.of();
            }

            List<Finding> failures =
                    config.requiredFiles.stream()
                            .filter(file -> !HarnessFiles.isSafeRegularFile(root, root.resolve(file)))
                            .map(file -> new Finding(
                                    config.getSeverity("requiredFiles"),
                                    "requiredFiles",
                                    "missing file: " + file))
                            .toList();

            List<Finding> dirFailures =
                    config.requiredDirectories.stream()
                            .filter(dir -> !HarnessFiles.isSafeDirectory(root, root.resolve(dir)))
                            .map(dir -> new Finding(
                                    config.getSeverity("requiredDirectories"),
                                    "requiredDirectories",
                                    "missing directory: " + dir))
                            .toList();

            List<Finding> keepFailures = validateKeepFiles(root, config);

            return Stream.of(failures, dirFailures, keepFailures)
                    .flatMap(List::stream)
                    .toList();
        }

        private List<Finding> validateKeepFiles(Path root, ManifestConfig config)
                throws MojoExecutionException {
            return config.emptyDirectoryKeepFiles.stream()
                    .flatMap(
                            keep -> {
                                try {
                                    Path keepPath = root.resolve(keep);
                                    Path directory = keepPath.getParent();
                                    if (!HarnessFiles.isSafeDirectory(root, directory)) {
                                        return Stream.empty();
                                    }
                                    List<Path> realFiles =
                                            HarnessFiles.safeFiles(root, directory).stream()
                                                    .filter(
                                                            f -> !f.getFileName()
                                                                    .toString()
                                                                    .equals(".gitkeep"))
                                                    .toList();
                                    if (realFiles.isEmpty()
                                            && !HarnessFiles.isSafeRegularFile(root, keepPath)) {
                                        return Stream.of(
                                                new Finding(
                                                        config.getSeverity("emptyDirectoryKeepFiles"),
                                                        "emptyDirectoryKeepFiles",
                                                        "empty directory must keep placeholder or real files: "
                                                                + root.relativize(directory)));
                                    }
                                    return Stream.empty();
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Validates required content in source files. */
    private static final class ContentValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            ManifestConfig config = loadManifestOrDefault(root);
            if (config == null) {
                return List.of();
            }

            List<Finding> docHeadingFailures = validateDocHeadings(root, config);
            List<Finding> contentCheckFailures = validateRequiredContent(root, config);

            return Stream.of(docHeadingFailures, contentCheckFailures)
                    .flatMap(List::stream)
                    .toList();
        }

        private List<Finding> validateDocHeadings(Path root, ManifestConfig config)
                throws MojoExecutionException {
            List<String> requiredAuthuredDocs =
                    config.requiredFiles.stream()
                            .filter(f -> f.startsWith("docs/") && f.endsWith(".md"))
                            .toList();

            return requiredAuthuredDocs.stream()
                    .flatMap(
                            doc -> {
                                try {
                                    Path file = root.resolve(doc);
                                    if (!HarnessFiles.isSafeRegularFile(root, file)) {
                                        return Stream.empty();
                                    }
                                    String text = HarnessFiles.read(file);
                                    return config.requiredDocHeadings.stream()
                                            .filter(heading -> !text.contains(heading))
                                            .map(heading -> new Finding(
                                                    config.getSeverity("requiredDocHeadings"),
                                                    "requiredDocHeadings",
                                                    "doc missing " + heading + ": " + doc));
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }

        private List<Finding> validateRequiredContent(Path root, ManifestConfig config)
                throws MojoExecutionException {
            return config.requiredContentChecks.stream()
                    .flatMap(
                            check -> {
                                try {
                                    String combined = check.files().stream()
                                            .map(f -> {
                                                try {
                                                    return HarnessFiles.read(root.resolve(f));
                                                } catch (MojoExecutionException e) {
                                                    return "";
                                                }
                                            })
                                            .reduce((a, b) -> a + "\n" + b)
                                            .orElse("");

                                    boolean hasAll = check.containsAll().stream()
                                            .allMatch(combined::contains);
                                    return hasAll
                                            ? Stream.empty()
                                            : Stream.of(new Finding(
                                                    config.getSeverity("requiredContentChecks"),
                                                    "requiredContentChecks",
                                                    check.failureMessage()));
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Validates agent frontmatter in .claude/agents. */
    private static final class AgentsValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            Pattern name = Pattern.compile("(?m)^name:\\s*[-a-z0-9]+\\s*$");
            Pattern description = Pattern.compile("(?m)^description:\\s*.+$");
            Path directory = root.resolve(".claude/agents");
            ManifestConfig config = loadManifestOrDefault(root);

            List<Path> files =
                    HarnessFiles.safeFiles(root, directory).stream()
                            .filter(f -> directory.equals(f.getParent()))
                            .filter(f -> f.getFileName().toString().endsWith(".md"))
                            .toList();

            if (files.isEmpty()) {
                String severity =
                        config != null ? config.getSeverity("agentFrontmatter") : "ERROR";
                return List.of(
                        new Finding(
                                severity,
                                "agentFrontmatter",
                                ".claude/agents must contain at least one .md agent"));
            }

            return files.stream()
                    .flatMap(
                            file -> {
                                try {
                                    String text = HarnessFiles.read(file);
                                    String relative = root.relativize(file).toString();
                                    String severity =
                                            config != null
                                                    ? config.getSeverity("agentFrontmatter")
                                                    : "ERROR";
                                    return Stream.of(
                                                    !text.startsWith("---")
                                                            ? Stream.of(
                                                                    new Finding(
                                                                            severity,
                                                                            "agentFrontmatter",
                                                                            "agent missing frontmatter: "
                                                                                    + relative))
                                                            : Stream.empty(),
                                                    !name.matcher(text).find()
                                                            ? Stream.of(
                                                                    new Finding(
                                                                            severity,
                                                                            "agentFrontmatter",
                                                                            "agent missing name: "
                                                                                    + relative))
                                                            : Stream.empty(),
                                                    !description.matcher(text).find()
                                                            ? Stream.of(
                                                                    new Finding(
                                                                            severity,
                                                                            "agentFrontmatter",
                                                                            "agent missing description: "
                                                                                    + relative))
                                                            : Stream.empty())
                                            .flatMap(s -> s);
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Validates skill SKILL.md frontmatter in .claude/skills. */
    private static final class SkillsValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            Pattern description = Pattern.compile("(?m)^description:\\s*.+$");
            List<Path> files =
                    HarnessFiles.safeFiles(root, root.resolve(".claude/skills")).stream()
                            .filter(f -> f.getFileName().toString().equals("SKILL.md"))
                            .toList();
            ManifestConfig config = loadManifestOrDefault(root);

            if (files.isEmpty()) {
                String severity =
                        config != null ? config.getSeverity("skillFrontmatter") : "ERROR";
                return List.of(
                        new Finding(
                                severity,
                                "skillFrontmatter",
                                ".claude/skills must contain at least one SKILL.md"));
            }

            return files.stream()
                    .flatMap(
                            file -> {
                                try {
                                    String text = HarnessFiles.read(file);
                                    String relative = root.relativize(file).toString();
                                    String severity =
                                            config != null
                                                    ? config.getSeverity("skillFrontmatter")
                                                    : "ERROR";
                                    return Stream.of(
                                                    !text.startsWith("---")
                                                            ? Stream.of(
                                                                    new Finding(
                                                                            severity,
                                                                            "skillFrontmatter",
                                                                            "skill missing frontmatter: "
                                                                                    + relative))
                                                            : Stream.empty(),
                                                    !description.matcher(text).find()
                                                            ? Stream.of(
                                                                    new Finding(
                                                                            severity,
                                                                            "skillFrontmatter",
                                                                            "skill missing description: "
                                                                                    + relative))
                                                            : Stream.empty())
                                            .flatMap(s -> s);
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Validates template group directories. */
    private static final class TemplateValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            ManifestConfig config = loadManifestOrDefault(root);
            if (config == null) {
                return List.of();
            }

            return config.templateGroups.stream()
                    .filter(
                            group ->
                                    !HarnessFiles.isSafeDirectory(
                                            root, root.resolve("docs/harness/templates/" + group)))
                    .map(group -> new Finding(
                            config.getSeverity("templateGroups"),
                            "templateGroups",
                            "missing template group: docs/harness/templates/" + group))
                    .toList();
        }
    }

    /** Validates active assets for scaffold leakage. */
    private static final class ActiveAssetsValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            ManifestConfig config = loadManifestOrDefault(root);
            if (config == null) {
                return List.of();
            }

            List<Path> excludedSubtrees =
                    config.excludedActiveAssetSubtrees.stream()
                            .map(root::resolve)
                            .toList();

            List<String> extensionPatterns =
                    config.activeAssetExtensions.stream()
                            .map(ext -> "\\." + Pattern.quote(ext) + "$")
                            .toList();
            String extensionRegex =
                    extensionPatterns.isEmpty()
                            ? ".*"
                            : ".*(" + String.join("|", extensionPatterns) + ")";

            return config.activeAssetBases.stream()
                    .flatMap(
                            baseName -> {
                                try {
                                    Path base = root.resolve(baseName);
                                    List<Path> files = HarnessFiles.safeFileOrWalk(root, base);
                                    return files.stream()
                                            .filter(
                                                    file -> {
                                                        for (Path excluded : excludedSubtrees) {
                                                            if (file.startsWith(excluded)) {
                                                                return false;
                                                            }
                                                        }
                                                        return file.toString().matches(extensionRegex);
                                                    })
                                            .flatMap(
                                                    file -> {
                                                        try {
                                                            String text = HarnessFiles.read(file);
                                                            return config.leakPatterns.stream()
                                                                    .filter(
                                                                            pattern ->
                                                                                    pattern.pattern()
                                                                                            .matcher(
                                                                                                    text)
                                                                                            .find())
                                                                    .map(
                                                                            pattern ->
                                                                                    new Finding(
                                                                                            config.getSeverity(
                                                                                                    "leakPatterns"),
                                                                                            "leakPatterns",
                                                                                            pattern.label()
                                                                                                    + " in active asset: "
                                                                                                    + root.relativize(
                                                                                                            file)));
                                                        } catch (MojoExecutionException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                    });
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Validates git hooks. */
    private static final class HooksValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            ManifestConfig config = loadManifestOrDefault(root);
            if (config == null) {
                return List.of();
            }

            String preCommitText = validateOneHook(root, "pre-commit");
            String prePushText = validateOneHook(root, "pre-push");

            List<Finding> preCommitFailures =
                    Pattern.compile(
                                    "(^|\\s)(uv|bun|gradle|mvn)(\\s|$)|\\./gradlew|harnessValidate|harness_validate\\.py|harness-validate\\.ts")
                            .matcher(preCommitText)
                            .find()
                                    ? List.of(
                                            new Finding(
                                                    config.getSeverity("hookValidationCommand"),
                                                    "hookValidationCommand",
                                                    "pre-commit hook must not run full stack validation commands"))
                                    : List.of();

            String command = hookCommand(prePushText);
            if (command.isBlank()) {
                return Stream.concat(
                                preCommitFailures.stream(),
                                Stream.of(
                                        new Finding(
                                                config.getSeverity("hookValidationCommand"),
                                                "hookValidationCommand",
                                                "pre-push hook must declare Harness validation command")))
                        .toList();
            }
            if (!config.expectedValidationCommand.equals(command)) {
                return Stream.concat(
                                preCommitFailures.stream(),
                                Stream.of(
                                        new Finding(
                                                config.getSeverity("hookValidationCommand"),
                                                "hookValidationCommand",
                                                "pre-push hook declares unsupported validation command: "
                                                        + command)))
                        .toList();
            }
            if (!List.of(prePushText.split("\\R")).contains(command)) {
                preCommitFailures =
                        Stream.concat(
                                        preCommitFailures.stream(),
                                        Stream.of(
                                                new Finding(
                                                        config.getSeverity("hookValidationCommand"),
                                                        "hookValidationCommand",
                                                        "pre-push hook must run the declared validation command")))
                                .toList();
            }

            List<Finding> ciFailures =
                    List.of(".github/workflows/harness.yml", ".gitlab-ci.yml").stream()
                            .flatMap(
                                    ciFile -> {
                                        try {
                                            Path path = root.resolve(ciFile);
                                            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                                                    && HarnessFiles.isSafeRegularFile(root, path)
                                                    && !HarnessFiles.read(path).contains(command)) {
                                                return Stream.of(
                                                        new Finding(
                                                                config.getSeverity("ciCommandMatch"),
                                                                "ciCommandMatch",
                                                                ciFile
                                                                        + ": CI command mismatch - expected "
                                                                        + command));
                                            }
                                            return Stream.empty();
                                        } catch (MojoExecutionException e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .toList();

            return Stream.of(preCommitFailures, ciFailures)
                    .flatMap(List::stream)
                    .toList();
        }

        private String validateOneHook(Path root, String name)
                throws MojoExecutionException {
            Path hook = root.resolve("docs/harness/git-hooks/" + name);
            if (!HarnessFiles.isSafeRegularFile(root, hook)) {
                return "";
            }
            return HarnessFiles.read(hook);
        }

        private String hookCommand(String prePushText) {
            for (String line : prePushText.split("\\R")) {
                if (line.startsWith("# Harness validation command: ")) {
                    return line.replace("# Harness validation command: ", "").trim();
                }
            }
            return "";
        }
    }

    /** Validates executable shell scripts with proper shebangs. */
    private static final class ShebangValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            ManifestConfig config = loadManifestOrDefault(root);
            if (config == null) {
                return List.of();
            }

            return config.envShebangBases.stream()
                    .flatMap(
                            baseName -> {
                                try {
                                    Path base = root.resolve(baseName);
                                    List<Path> files = HarnessFiles.safeFileOrWalk(root, base);
                                    return files.stream()
                                            .filter(
                                                    file -> {
                                                        try {
                                                            String text = HarnessFiles.read(file);
                                                            return text.contains("#!/")
                                                                    && (text.endsWith(".sh")
                                                                            || text.contains(".sh\n"));
                                                        } catch (MojoExecutionException e) {
                                                            return false;
                                                        }
                                                    })
                                            .flatMap(
                                                    file -> {
                                                        try {
                                                            String text = HarnessFiles.read(file);
                                                            if (!text.startsWith("#!/usr/bin/env sh\n")) {
                                                                return Stream.of(
                                                                        new Finding(
                                                                                config.getSeverity(
                                                                                        "envShebang"),
                                                                                "envShebang",
                                                                                "executable script must use #!/usr/bin/env sh: "
                                                                                        + root.relativize(
                                                                                                file)));
                                                            }
                                                            return Stream.empty();
                                                        } catch (MojoExecutionException e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                    });
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Validates that completed plans have no unchecked task boxes. */
    private static final class PlanCompletionValidator {
        List<Finding> validate(Path root) throws MojoExecutionException {
            ManifestConfig config = loadManifestOrDefault(root);
            if (config == null || config.completedPlanDirectory.isBlank()) {
                return List.of();
            }

            Path completedDir = root.resolve(config.completedPlanDirectory);
            if (!HarnessFiles.isSafeDirectory(root, completedDir)) {
                return List.of();
            }

            List<Path> planFiles =
                    HarnessFiles.safeFiles(root, completedDir).stream()
                            .filter(f -> f.getFileName().toString().endsWith(".md"))
                            .filter(f -> !f.getFileName().toString().equals(".gitkeep"))
                            .toList();

            Pattern unfinishedPattern = Pattern.compile(config.unfinishedTaskPattern);

            return planFiles.stream()
                    .flatMap(
                            file -> {
                                try {
                                    String text = HarnessFiles.read(file);
                                    if (unfinishedPattern.matcher(text).find()) {
                                        return Stream.of(
                                                new Finding(
                                                        config.getSeverity("completedPlanUnfinishedTask"),
                                                        "completedPlanUnfinishedTask",
                                                        "completed plan has unchecked tasks: "
                                                                + root.relativize(file)));
                                    }
                                    return Stream.empty();
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Shared file utilities for harness validation. */
    private static final class HarnessFiles {
        private static boolean isSafeRegularFile(Path root, Path path) {
            if (Files.isSymbolicLink(path)) {
                if (allowedRootContractTarget(root, path) != null) {
                    return true;
                }
                return false;
            }
            return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        }

        private static boolean isSafeDirectory(Path root, Path path) {
            if (Files.isSymbolicLink(path)) {
                return false;
            }
            return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
        }

        private static List<Path> safeFiles(Path root, Path base) throws MojoExecutionException {
            if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) {
                return List.of();
            }
            if (Files.isSymbolicLink(base)) {
                return List.of();
            }
            if (Files.isRegularFile(base, LinkOption.NOFOLLOW_LINKS)) {
                return List.of(base);
            }
            try (Stream<Path> stream = Files.walk(base)) {
                return stream
                        .filter(f -> !f.equals(base))
                        .filter(f -> !Files.isSymbolicLink(f))
                        .filter(f -> Files.isRegularFile(f, LinkOption.NOFOLLOW_LINKS))
                        .toList();
            } catch (IOException error) {
                throw new MojoExecutionException("failed to inspect " + base, error);
            }
        }

        private static List<Path> safeFileOrWalk(Path root, Path base)
                throws MojoExecutionException {
            if (Files.isSymbolicLink(base) && allowedRootContractTarget(root, base) == null) {
                return List.of();
            }
            if (isSafeRegularFile(root, base)) {
                return List.of(base);
            }
            return safeFiles(root, base);
        }

        private static Path allowedRootContractTarget(Path root, Path path) {
            Path normalizedRoot = root.normalize();
            Path fileName = path.getFileName();
            if (fileName == null
                    || path.getParent() == null
                    || !path.getParent().normalize().equals(normalizedRoot)) {
                return null;
            }
            String name = fileName.toString();
            if (!name.equals("AGENTS.md") && !name.equals("CLAUDE.md")) {
                return null;
            }
            String expected = name.equals("AGENTS.md") ? "CLAUDE.md" : "AGENTS.md";
            try {
                Path targetName = Files.readSymbolicLink(path);
                if (targetName.getNameCount() != 1 || !targetName.toString().equals(expected)) {
                    return null;
                }
                Path target = normalizedRoot.resolve(targetName).normalize();
                if (target.getParent().equals(normalizedRoot)
                        && !Files.isSymbolicLink(target)
                        && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    return target;
                }
                return null;
            } catch (IOException error) {
                return null;
            }
        }

        private static String read(Path path) throws MojoExecutionException {
            try {
                if (Files.isSymbolicLink(path)) {
                    Path target = allowedRootContractTarget(path.getParent(), path);
                    if (target == null) {
                        return "";
                    }
                    return Files.readString(target, StandardCharsets.UTF_8);
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
                return "";
            } catch (IOException error) {
                throw new MojoExecutionException("failed to read " + path, error);
            }
        }
    }

    /** Pattern and label used when scanning active files for scaffold leakage. */
    private static final class LeakPattern {
        private final Pattern pattern;
        private final String label;

        private LeakPattern(Pattern pattern, String label) {
            this.pattern = pattern;
            this.label = label;
        }

        private Pattern pattern() {
            return pattern;
        }

        private String label() {
            return label;
        }
    }

    /** Loads and caches manifest configuration. */
    private static ManifestConfig loadManifestOrDefault(Path root)
            throws MojoExecutionException {
        Path manifestPath = root.resolve(MANIFEST_PATH);
        String manifest = HarnessFiles.read(manifestPath);
        if (manifest.isBlank()) {
            return null;
        }
        try {
            return new ManifestConfig(manifest);
        } catch (Exception e) {
            return null;
        }
    }
}
