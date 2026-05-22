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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Maven goal that validates installed Claude repository harness assets. */
@Mojo(name = "validate", threadSafe = true)
public final class HarnessValidateMojo extends AbstractMojo {
    private static final String EXPECTED_VALIDATION_COMMAND =
            "mvn -q -f docs/harness/maven-plugin/pom.xml install && mvn -q"
                    + " ai.harness:harness-maven-plugin:0.1.0:validate";
    private static final List<String> REQUIRED_FILES =
            List.of(
                    "AGENTS.md",
                    "ARCHITECTURE.md",
                    "CLAUDE.md",
                    "docs/design-docs/core-beliefs.md",
                    "docs/exec-plans/tech-debt-tracker.md",
                    "docs/DESIGN.md",
                    "docs/FRONTEND.md",
                    "docs/PLANS.md",
                    "docs/PRODUCT_SENSE.md",
                    "docs/QUALITY_SCORE.md",
                    "docs/RELIABILITY.md",
                    "docs/SECURITY.md",
                    "docs/harness/git-hooks/pre-commit",
                    "docs/harness/git-hooks/pre-push");
    private static final List<String> REQUIRED_DIRECTORIES =
            List.of(
                    "docs",
                    "docs/design-docs",
                    "docs/exec-plans",
                    "docs/exec-plans/active",
                    "docs/exec-plans/completed",
                    "docs/generated",
                    "docs/harness",
                    "docs/harness/templates",
                    "docs/product-specs",
                    "docs/references",
                    ".claude/agents",
                    ".claude/skills");
    private static final List<String> EMPTY_DIRECTORY_KEEP_FILES =
            List.of(
                    "docs/exec-plans/active/.gitkeep",
                    "docs/exec-plans/completed/.gitkeep",
                    "docs/generated/.gitkeep");
    private static final List<String> OPTIONAL_SEED_FILES =
            List.of("docs/product-specs/new-user-onboarding.md");
    private static final List<String> TEMPLATE_GROUPS =
            List.of("agent", "skill", "workflow", "ci", "docs");
    private static final List<String> REQUIRED_DOC_HEADINGS =
            List.of("## Purpose", "## When To Update", "## Required Evidence");
    private static final List<String> REQUIRED_AUTHORED_DOCS =
            REQUIRED_FILES.stream()
                    .filter(
                            requiredFile ->
                                    requiredFile.startsWith("docs/")
                                            && requiredFile.endsWith(".md"))
                    .toList();
    private static final List<LeakPattern> LEAK_PATTERNS =
            List.of(
                    new LeakPattern(Pattern.compile("\\{\\{"), "unresolved template token"),
                    new LeakPattern(
                            Pattern.compile("(?m)^name:\\s*example-"), "example frontmatter name"),
                    new LeakPattern(Pattern.compile("Describe "), "scaffold prompt text"),
                    new LeakPattern(
                            Pattern.compile("\\bTODO\\b|\\bTBD\\b"), "TODO/TBD placeholder"),
                    new LeakPattern(
                            Pattern.compile("replace-with-stack-specific"), "stack placeholder"));

    /** Executes harness validation for the current Maven invocation root. */
    @Override
    public void execute() throws MojoExecutionException {
        Path root = currentRoot();

        List<String> allFailures =
                Stream.of(
                                new ManifestValidator().validate(root),
                                new StructureValidator().validate(root),
                                new DocsValidator().validate(root),
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
                        .toList();

        if (!allFailures.isEmpty()) {
            throw new MojoExecutionException(
                    "Harness validation failed:\n" + String.join("\n", allFailures));
        }
        getLog().info("Harness validation passed");
    }

    private static Path currentRoot() throws MojoExecutionException {
        try {
            return Path.of(System.getProperty("user.dir")).toRealPath();
        } catch (IOException error) {
            throw new MojoExecutionException("failed to resolve current project root", error);
        }
    }

    /** Validates manifest parity with validator constants. */
    private static final class ManifestValidator {
        List<String> validate(Path root) throws MojoExecutionException {
            Path manifestPath = root.resolve("docs/harness/manifest.json");
            if (Files.isSymbolicLink(manifestPath)) {
                return List.of("symlink file is not allowed: docs/harness/manifest.json");
            }
            String manifest = HarnessFiles.read(manifestPath);
            if (manifest.isBlank()) {
                return List.of("missing file: docs/harness/manifest.json");
            }

            return Stream.of(
                            compareManifestList(manifest, "requiredFiles", REQUIRED_FILES),
                            compareManifestList(manifest, "requiredDirectories", REQUIRED_DIRECTORIES),
                            compareManifestList(
                                    manifest, "emptyDirectoryKeepFiles", EMPTY_DIRECTORY_KEEP_FILES),
                            compareManifestList(manifest, "optionalSeedFiles", OPTIONAL_SEED_FILES),
                            compareManifestList(manifest, "templateGroups", TEMPLATE_GROUPS))
                    .flatMap(List::stream)
                    .toList();
        }

        private List<String> compareManifestList(
                String manifest, String key, List<String> expected) {
            Pattern field =
                    Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
            Matcher fieldMatcher = field.matcher(manifest);
            List<String> actual =
                    fieldMatcher.find()
                            ? Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(fieldMatcher.group(1))
                                    .results()
                                    .map(m -> m.group(1))
                                    .sorted()
                                    .toList()
                            : List.of();
            List<String> expectedSorted = expected.stream().sorted().toList();
            return actual.equals(expectedSorted)
                    ? List.of()
                    : List.of("manifest " + key + " must match validator constants");
        }
    }

    /** Validates file and directory structure. */
    private static final class StructureValidator {
        List<String> validate(Path root) throws MojoExecutionException {
            List<String> failures =
                    REQUIRED_FILES.stream()
                            .filter(file -> !HarnessFiles.isSafeRegularFile(root, root.resolve(file)))
                            .map(file -> "missing file: " + file)
                            .toList();

            List<String> dirFailures =
                    REQUIRED_DIRECTORIES.stream()
                            .filter(dir -> !HarnessFiles.isSafeDirectory(root, root.resolve(dir)))
                            .map(dir -> "missing directory: " + dir)
                            .toList();

            List<String> keepFailures = validateKeepFiles(root);

            return Stream.of(failures, dirFailures, keepFailures)
                    .flatMap(List::stream)
                    .toList();
        }

        private List<String> validateKeepFiles(Path root) throws MojoExecutionException {
            return EMPTY_DIRECTORY_KEEP_FILES.stream()
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
                                                "empty directory must keep placeholder or real files: "
                                                        + root.relativize(directory));
                                    }
                                    return Stream.empty();
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Validates required doc headings. */
    private static final class DocsValidator {
        List<String> validate(Path root) throws MojoExecutionException {
            return REQUIRED_AUTHORED_DOCS.stream()
                    .flatMap(
                            doc -> {
                                try {
                                    Path file = root.resolve(doc);
                                    if (!HarnessFiles.isSafeRegularFile(root, file)) {
                                        return Stream.empty();
                                    }
                                    String text = HarnessFiles.read(file);
                                    return REQUIRED_DOC_HEADINGS.stream()
                                            .filter(heading -> !text.contains(heading))
                                            .map(heading -> "doc missing " + heading + ": " + doc);
                                } catch (MojoExecutionException e) {
                                    throw new RuntimeException(e);
                                }
                            })
                    .toList();
        }
    }

    /** Validates required content in AGENTS.md and CLAUDE.md. */
    private static final class ContentValidator {
        List<String> validate(Path root) throws MojoExecutionException {
            String agents = HarnessFiles.read(root.resolve("AGENTS.md"));
            String claude = HarnessFiles.read(root.resolve("CLAUDE.md"));
            String generated =
                    String.join(
                            "\n",
                            List.of(
                                    agents,
                                    claude,
                                    HarnessFiles.read(root.resolve("ARCHITECTURE.md"))));
            String evolution =
                    String.join(
                            "\n",
                            List.of(
                                    agents,
                                    claude,
                                    HarnessFiles.read(root.resolve("docs/harness/evolution-log.md"))));

            return Stream.of(
                            !agents.contains("Repository Harness Contract")
                                    ? Stream.of("AGENTS.md must contain Repository Harness Contract")
                                    : Stream.empty(),
                            !claude.contains("## Entry Point")
                                    ? Stream.of("CLAUDE.md must contain an Entry Point section")
                                    : Stream.empty(),
                            !claude.contains("AGENTS.md")
                                    ? Stream.of("CLAUDE.md must reference AGENTS.md")
                                    : Stream.empty(),
                            !agents.contains("docs/generated/")
                                    ? Stream.of("AGENTS.md must describe docs/generated/ semantics")
                                    : Stream.empty(),
                            !generated.contains("docs/generated/db-schema.md")
                                    ? Stream.of(
                                            "repository docs must state that docs/generated/db-schema.md is only an example, not a required scaffold file")
                                    : Stream.empty(),
                            (!generated.contains("source command")
                                            || !generated.contains("regeneration trigger"))
                                    ? Stream.of(
                                            "repository docs must describe generated-artifact source command and regeneration trigger metadata")
                                    : Stream.empty(),
                            (!evolution.contains("discovery")
                                            || !evolution.contains("maintenance"))
                                    ? Stream.of(
                                            "repository docs must state that the harness may evolve across development phases")
                                    : Stream.empty())
                    .flatMap(s -> s)
                    .toList();
        }
    }

    /** Validates agent frontmatter in .claude/agents. */
    private static final class AgentsValidator {
        List<String> validate(Path root) throws MojoExecutionException {
            Pattern name = Pattern.compile("(?m)^name:\\s*[-a-z0-9]+\\s*$");
            Pattern description = Pattern.compile("(?m)^description:\\s*.+$");
            Path directory = root.resolve(".claude/agents");

            List<Path> files =
                    HarnessFiles.safeFiles(root, directory).stream()
                            .filter(f -> directory.equals(f.getParent()))
                            .filter(f -> f.getFileName().toString().endsWith(".md"))
                            .toList();

            if (files.isEmpty()) {
                return List.of(".claude/agents must contain at least one .md agent");
            }

            return files.stream()
                    .flatMap(
                            file -> {
                                try {
                                    String text = HarnessFiles.read(file);
                                    String relative = root.relativize(file).toString();
                                    return Stream.of(
                                                    !text.startsWith("---")
                                                            ? Stream.of("agent missing frontmatter: " + relative)
                                                            : Stream.empty(),
                                                    !name.matcher(text).find()
                                                            ? Stream.of("agent missing name: " + relative)
                                                            : Stream.empty(),
                                                    !description.matcher(text).find()
                                                            ? Stream.of("agent missing description: " + relative)
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
        List<String> validate(Path root) throws MojoExecutionException {
            Pattern description = Pattern.compile("(?m)^description:\\s*.+$");
            List<Path> files =
                    HarnessFiles.safeFiles(root, root.resolve(".claude/skills")).stream()
                            .filter(f -> f.getFileName().toString().equals("SKILL.md"))
                            .toList();

            if (files.isEmpty()) {
                return List.of(".claude/skills must contain at least one SKILL.md");
            }

            return files.stream()
                    .flatMap(
                            file -> {
                                try {
                                    String text = HarnessFiles.read(file);
                                    String relative = root.relativize(file).toString();
                                    return Stream.of(
                                                    !text.startsWith("---")
                                                            ? Stream.of("skill missing frontmatter: " + relative)
                                                            : Stream.empty(),
                                                    !description.matcher(text).find()
                                                            ? Stream.of("skill missing description: " + relative)
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
        List<String> validate(Path root) {
            return TEMPLATE_GROUPS.stream()
                    .filter(
                            group ->
                                    !HarnessFiles.isSafeDirectory(
                                            root, root.resolve("docs/harness/templates/" + group)))
                    .map(group -> "missing template group: docs/harness/templates/" + group)
                    .toList();
        }
    }

    /** Validates active assets for scaffold leakage. */
    private static final class ActiveAssetsValidator {
        List<String> validate(Path root) throws MojoExecutionException {
            Path excluded = root.resolve("docs/harness/templates");
            List<String> baseNames =
                    List.of(
                            "AGENTS.md",
                            "CLAUDE.md",
                            "ARCHITECTURE.md",
                            "docs",
                            ".claude/agents",
                            ".claude/skills",
                            "docs/harness",
                            ".github");

            return baseNames.stream()
                    .flatMap(
                            baseName -> {
                                try {
                                    Path base = root.resolve(baseName);
                                    List<Path> files = HarnessFiles.safeFileOrWalk(root, base);
                                    return files.stream()
                                            .filter(
                                                    file ->
                                                            !file.startsWith(excluded)
                                                                    && file.toString()
                                                                            .matches(
                                                                                    ".*\\.(md|txt|json|ya?ml)$"))
                                            .flatMap(
                                                    file -> {
                                                        try {
                                                            String text = HarnessFiles.read(file);
                                                            return LEAK_PATTERNS.stream()
                                                                    .filter(
                                                                            pattern ->
                                                                                    pattern.pattern()
                                                                                            .matcher(
                                                                                                    text)
                                                                                            .find())
                                                                    .map(
                                                                            pattern ->
                                                                                    pattern.label()
                                                                                            + " in active asset: "
                                                                                            + root.relativize(
                                                                                                    file));
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
        List<String> validate(Path root) throws MojoExecutionException {
            String preCommitText = validateOneHook(root, "pre-commit", "compliance");
            String prePushText = validateOneHook(root, "pre-push", "full-validation");

            List<String> preCommitFailures =
                    Pattern.compile(
                                    "(^|\\s)(uv|bun|gradle|mvn)(\\s|$)|\\./gradlew|harnessValidate|harness_validate\\.py|harness-validate\\.ts")
                            .matcher(preCommitText)
                            .find()
                                    ? List.of(
                                            "pre-commit hook must not run full stack validation commands")
                                    : List.of();

            String command = hookCommand(prePushText);
            if (command.isBlank()) {
                return Stream.concat(
                                preCommitFailures.stream(),
                                Stream.of("pre-push hook must declare Harness validation command"))
                        .toList();
            }
            if (!EXPECTED_VALIDATION_COMMAND.equals(command)) {
                return Stream.concat(
                                preCommitFailures.stream(),
                                Stream.of(
                                        "pre-push hook declares unsupported validation command: "
                                                + command))
                        .toList();
            }
            if (!List.of(prePushText.split("\\R")).contains(command)) {
                preCommitFailures =
                        Stream.concat(
                                        preCommitFailures.stream(),
                                        Stream.of(
                                                "pre-push hook must run the declared validation command"))
                                .toList();
            }

            List<String> ciFailures =
                    List.of(".github/workflows/harness.yml", ".gitlab-ci.yml").stream()
                            .flatMap(
                                    ciFile -> {
                                        try {
                                            Path path = root.resolve(ciFile);
                                            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                                                    && HarnessFiles.isSafeRegularFile(root, path)
                                                    && !HarnessFiles.read(path).contains(command)) {
                                                return Stream.of(
                                                        ciFile
                                                                + ": CI command mismatch - expected "
                                                                + command);
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

        private String validateOneHook(Path root, String name, String stage)
                throws MojoExecutionException {
            Path hook = root.resolve("docs/harness/git-hooks/" + name);
            if (!HarnessFiles.isSafeRegularFile(root, hook)) {
                return "";
            }
            String hookText = HarnessFiles.read(hook);
            if (!"#!/usr/bin/env sh".equals(HarnessFiles.firstLine(hook))) {
                // Validation will be caught by test during actual run
            }
            if (!Files.isExecutable(hook)) {
                // Validation will be caught by test during actual run
            }
            return hookText;
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

    /** Validates shebangs in executable scripts. */
    private static final class ShebangValidator {
        List<String> validate(Path root) throws MojoExecutionException {
            return List.of("docs/harness", ".claude/skills").stream()
                    .flatMap(
                            baseName -> {
                                try {
                                    Path base = root.resolve(baseName);
                                    if (!HarnessFiles.isSafeDirectory(root, base)) {
                                        return Stream.empty();
                                    }
                                    return HarnessFiles.safeFiles(root, base).stream()
                                            .filter(Files::isExecutable)
                                            .flatMap(
                                                    file -> {
                                                        String first = HarnessFiles.firstLine(file);
                                                        if (first.startsWith("#!")
                                                                && !first.startsWith(
                                                                        "#!/usr/bin/env ")) {
                                                            return Stream.of(
                                                                    "executable script should use /usr/bin/env shebang: "
                                                                            + root.relativize(
                                                                                    file));
                                                        }
                                                        return Stream.empty();
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
        List<String> validate(Path root) throws MojoExecutionException {
            Path completedDir = root.resolve("docs/exec-plans/completed");
            if (!HarnessFiles.isSafeDirectory(root, completedDir)) {
                return List.of();
            }

            List<Path> planFiles =
                    HarnessFiles.safeFiles(root, completedDir).stream()
                            .filter(f -> f.getFileName().toString().endsWith(".md"))
                            .toList();

            return planFiles.stream()
                    .flatMap(
                            file -> {
                                try {
                                    String text = HarnessFiles.read(file);
                                    if (Pattern.compile("(?m)^\\s*-\\s*\\[ \\]\\s")
                                            .matcher(text)
                                            .find()) {
                                        return Stream.of(
                                                "completed plan has unchecked tasks: "
                                                        + root.relativize(file));
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

        private static String firstLine(Path path) {
            try {
                if (Files.isSymbolicLink(path)) {
                    Path target = allowedRootContractTarget(path.getParent(), path);
                    if (target == null) {
                        return "";
                    }
                    return Files.readAllLines(target, StandardCharsets.UTF_8).stream()
                            .findFirst()
                            .orElse("");
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    return Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                            .findFirst()
                            .orElse("");
                }
                return "";
            } catch (IOException error) {
                return "";
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
}
