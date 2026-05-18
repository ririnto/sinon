package ai.harness.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Maven goal that validates installed Claude repository harness assets.
 */
@Mojo(name = "validate", threadSafe = true)
public final class HarnessValidateMojo extends AbstractMojo {
    private static final String EXPECTED_VALIDATION_COMMAND =
        "mvn -q -f .claude/harness/maven-plugin/pom.xml install && mvn -q ai.harness:harness-maven-plugin:0.1.0:validate";
    private static final List<String> REQUIRED_FILES = List.of(
        "AGENTS.md",
        "ARCHITECTURE.md",
        "CLAUDE.md",
        "docs/design-docs/index.md",
        "docs/design-docs/core-beliefs.md",
        "docs/exec-plans/tech-debt-tracker.md",
        "docs/product-specs/index.md",
        "docs/DESIGN.md",
        "docs/FRONTEND.md",
        "docs/PLANS.md",
        "docs/PRODUCT_SENSE.md",
        "docs/QUALITY_SCORE.md",
        "docs/RELIABILITY.md",
        "docs/SECURITY.md",
        ".claude/harness/git-hooks/pre-commit",
        ".claude/harness/git-hooks/pre-push"
    );
    private static final List<String> REQUIRED_DIRECTORIES = List.of(
        "docs",
        "docs/design-docs",
        "docs/exec-plans",
        "docs/exec-plans/active",
        "docs/exec-plans/completed",
        "docs/generated",
        "docs/product-specs",
        "docs/references",
        ".claude/agents",
        ".claude/skills",
        ".claude/harness/templates"
    );
    private static final List<String> EMPTY_DIRECTORY_KEEP_FILES = List.of(
        "docs/exec-plans/active/.gitkeep",
        "docs/exec-plans/completed/.gitkeep",
        "docs/generated/.gitkeep"
    );
    private static final List<String> OPTIONAL_SEED_FILES = List.of(
        "docs/product-specs/new-user-onboarding.md",
        "docs/references/design-system-reference-llms.txt",
        "docs/references/nixpacks-llms.txt",
        "docs/references/uv-llms.txt"
    );
    private static final List<String> TEMPLATE_GROUPS = List.of(
        "agent",
        "skill",
        "workflow",
        "ci",
        "docs"
    );
    private static final List<String> REQUIRED_DOC_HEADINGS = List.of(
        "## Purpose",
        "## When To Update",
        "## Required Evidence",
        "## Validation Link"
    );
    private static final List<String> REQUIRED_AUTHORED_DOCS = REQUIRED_FILES
        .stream()
        .filter(requiredFile -> requiredFile.startsWith("docs/") && requiredFile.endsWith(".md"))
        .toList();
    private static final List<LeakPattern> LEAK_PATTERNS = List.of(
        new LeakPattern(Pattern.compile("\\{\\{"), "unresolved template token"),
        new LeakPattern(Pattern.compile("(?m)^name:\\s*example-"), "example frontmatter name"),
        new LeakPattern(Pattern.compile("Describe "), "scaffold prompt text"),
        new LeakPattern(Pattern.compile("\\bTODO\\b|\\bTBD\\b"), "TODO/TBD placeholder"),
        new LeakPattern(Pattern.compile("replace-with-stack-specific"), "stack placeholder")
    );

    /**
     * Executes harness validation for the current Maven invocation root.
     */
    @Override
    public void execute() throws MojoExecutionException {
        Path root = currentRoot();
        List<String> failures = new ArrayList<>();
        validateManifestParity(root, failures);
        for (String requiredFile : REQUIRED_FILES) {
            if (!isSafeRegularFile(root, root.resolve(requiredFile), failures)) {
                failures.add("missing file: " + requiredFile);
            }
        }
        for (String requiredDirectory : REQUIRED_DIRECTORIES) {
            if (!isSafeDirectory(root, root.resolve(requiredDirectory), failures)) {
                failures.add("missing directory: " + requiredDirectory);
            }
        }
        validateKeepFiles(root, failures);
        validateDocs(root, failures);
        String agents = read(root.resolve("AGENTS.md"));
        String claude = read(root.resolve("CLAUDE.md"));
        String generated = String.join(
            "\n",
            List.of(
                agents,
                claude,
                read(root.resolve("ARCHITECTURE.md"))
            )
        );
        String evolution = String.join(
            "\n",
            List.of(
                agents,
                claude,
                read(root.resolve(".claude/harness/evolution-log.md"))
            )
        );
        failures.addAll(requiredContentFailures(agents, claude, generated, evolution));
        validateAgents(root, failures);
        validateSkills(root, failures);
        for (String group : TEMPLATE_GROUPS) {
            if (!isSafeDirectory(root, root.resolve(".claude/harness/templates/" + group), failures)) {
                failures.add("missing template group: .claude/harness/templates/" + group);
            }
        }
        validateActiveAssets(root, failures);
        validateHooks(root, failures);
        validateEnvShebangs(root, failures);
        if (!failures.isEmpty()) {
            throw new MojoExecutionException(
                "Harness validation failed:\n" + String.join("\n", failures.stream().distinct().toList())
            );
        }
        getLog().info("Harness validation passed");
    }

    private static List<String> requiredContentFailures(
        String agents,
        String claude,
        String generated,
        String evolution
    ) {
        List<String> failures = new ArrayList<>();
        if (!agents.contains("Repository Harness Contract")) {
            failures.add("AGENTS.md must contain Repository Harness Contract");
        }
        if (!claude.contains("Claude Code Entry Point")) {
            failures.add("CLAUDE.md must contain Claude Code Entry Point");
        }
        if (!claude.contains("AGENTS.md")) {
            failures.add("CLAUDE.md must reference AGENTS.md");
        }
        if (!agents.contains("docs/generated/")) {
            failures.add("AGENTS.md must describe docs/generated/ semantics");
        }
        if (!generated.contains("docs/generated/db-schema.md")) {
            failures.add(
                "repository docs must state that docs/generated/db-schema.md is only an example, "
                    + "not a required scaffold file"
            );
        }
        if (
            !generated.contains("source command")
                || !generated.contains("regeneration trigger")
        ) {
            failures.add(
                "repository docs must describe generated-artifact source command and "
                    + "regeneration trigger metadata"
            );
        }
        if (
            !evolution.contains("discovery")
                || !evolution.contains("maintenance")
        ) {
            failures.add(
                "repository docs must state that the harness may evolve across development phases"
            );
        }
        return failures;
    }

    private static Path currentRoot() throws MojoExecutionException {
        try {
            return Path.of(System.getProperty("user.dir")).toRealPath();
        } catch (IOException error) {
            throw new MojoExecutionException("failed to resolve current project root", error);
        }
    }

    private static void validateManifestParity(Path root, List<String> failures) throws MojoExecutionException {
        Path manifestPath = root.resolve(".claude/harness/manifest.json");
        if (Files.isSymbolicLink(manifestPath)) {
            failures.add("symlink file is not allowed: .claude/harness/manifest.json");
            return;
        }
        String manifest = read(manifestPath);
        if (manifest.isBlank()) {
            failures.add("missing file: .claude/harness/manifest.json");
            return;
        }
        compareManifestList(manifest, "requiredFiles", REQUIRED_FILES, failures);
        compareManifestList(manifest, "requiredDirectories", REQUIRED_DIRECTORIES, failures);
        compareManifestList(manifest, "emptyDirectoryKeepFiles", EMPTY_DIRECTORY_KEEP_FILES, failures);
        compareManifestList(manifest, "optionalSeedFiles", OPTIONAL_SEED_FILES, failures);
        compareManifestList(manifest, "templateGroups", TEMPLATE_GROUPS, failures);
    }

    private static void compareManifestList(String manifest, String key, List<String> expected, List<String> failures) {
        Pattern field = Pattern.compile(
            "\\\"" + key + "\\\"\\s*:\\s*\\[(.*?)]",
            Pattern.DOTALL
        );
        Matcher fieldMatcher = field.matcher(manifest);
        List<String> actual = new ArrayList<>();
        if (fieldMatcher.find()) {
            Matcher itemMatcher = Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(fieldMatcher.group(1));
            while (itemMatcher.find()) {
                actual.add(itemMatcher.group(1));
            }
        }
        if (!actual.stream().sorted().toList().equals(expected.stream().sorted().toList())) {
            failures.add("manifest " + key + " must match validator constants");
        }
    }

    private static void validateKeepFiles(Path root, List<String> failures) throws MojoExecutionException {
        for (String keep : EMPTY_DIRECTORY_KEEP_FILES) {
            Path keepPath = root.resolve(keep);
            Path directory = keepPath.getParent();
            if (!isSafeDirectory(root, directory, failures)) {
                continue;
            }
            List<Path> realFiles = safeFiles(root, directory, failures)
                .stream()
                .filter(candidateFile -> !candidateFile.getFileName().toString().equals(".gitkeep"))
                .toList();
            if (realFiles.isEmpty() && !isSafeRegularFile(root, keepPath, failures)) {
                failures.add("empty directory must keep placeholder or real files: " + root.relativize(directory));
            }
        }
    }

    private static void validateDocs(Path root, List<String> failures) throws MojoExecutionException {
        for (String doc : REQUIRED_AUTHORED_DOCS) {
            Path file = root.resolve(doc);
            if (!isSafeRegularFile(root, file, failures)) {
                continue;
            }
            String text = read(file);
            for (String heading : REQUIRED_DOC_HEADINGS) {
                if (!text.contains(heading)) {
                    failures.add("doc missing " + heading + ": " + doc);
                }
            }
        }
    }

    private static void validateAgents(Path root, List<String> failures) throws MojoExecutionException {
        Pattern name = Pattern.compile("(?m)^name:\\s*[-a-z0-9]+\\s*$");
        Pattern description = Pattern.compile("(?m)^description:\\s*.+$");
        Path directory = root.resolve(".claude/agents");
        List<Path> files = safeFiles(root, directory, failures)
            .stream()
            .filter(candidateFile -> directory.equals(candidateFile.getParent()))
            .filter(candidateFile -> candidateFile.getFileName().toString().endsWith(".md"))
            .toList();
        if (files.isEmpty()) {
            failures.add(".claude/agents must contain at least one .md agent");
        }
        for (Path file : files) {
            String text = read(file);
            String relative = root.relativize(file).toString();
            if (!text.startsWith("---")) {
                failures.add("agent missing frontmatter: " + relative);
            }
            if (!name.matcher(text).find()) {
                failures.add("agent missing name: " + relative);
            }
            if (!description.matcher(text).find()) {
                failures.add("agent missing description: " + relative);
            }
        }
    }

    private static void validateSkills(Path root, List<String> failures) throws MojoExecutionException {
        Pattern description = Pattern.compile("(?m)^description:\\s*.+$");
        List<Path> files = safeFiles(root, root.resolve(".claude/skills"), failures)
            .stream()
            .filter(candidateFile -> candidateFile.getFileName().toString().equals("SKILL.md"))
            .toList();
        if (files.isEmpty()) {
            failures.add(".claude/skills must contain at least one SKILL.md");
        }
        for (Path file : files) {
            String text = read(file);
            String relative = root.relativize(file).toString();
            if (!text.startsWith("---")) {
                failures.add("skill missing frontmatter: " + relative);
            }
            if (!description.matcher(text).find()) {
                failures.add("skill missing description: " + relative);
            }
        }
    }

    private static void validateActiveAssets(Path root, List<String> failures) throws MojoExecutionException {
        Path excluded = root.resolve(".claude/harness/templates");
        for (String baseName : List.of(
            "AGENTS.md",
            "CLAUDE.md",
            "ARCHITECTURE.md",
            "docs",
            ".claude/agents",
            ".claude/skills",
            ".claude/harness",
            ".github"
        )) {
            Path base = root.resolve(baseName);
            List<Path> files = safeFileOrWalk(root, base, failures);
            for (Path file : files) {
                if (
                    file.startsWith(excluded)
                        || !file.toString().matches(".*\\.(md|txt|json|ya?ml)$")
                ) {
                    continue;
                }
                String text = read(file);
                for (LeakPattern pattern : LEAK_PATTERNS) {
                    if (pattern.pattern().matcher(text).find()) {
                        failures.add(pattern.label() + " in active asset: " + root.relativize(file));
                    }
                }
            }
        }
    }

    private static String hookCommand(String prePushText) {
        for (String line : prePushText.split("\\R")) {
            if (line.startsWith("# Harness validation command: ")) {
                return line.replace("# Harness validation command: ", "").trim();
            }
        }
        return "";
    }

    private static String validateOneHook(Path root, String name, String stage, List<String> failures)
        throws MojoExecutionException {
        Path hook = root.resolve(".claude/harness/git-hooks/" + name);
        String hookText = "";
        if (isSafeRegularFile(root, hook, failures)) {
            hookText = read(hook);
            if (!"#!/usr/bin/env sh".equals(firstLine(hook))) {
                failures.add(name + " hook must use #!/usr/bin/env sh");
            }
            if (!Files.isExecutable(hook)) {
                failures.add(name + " hook must be executable: " + root.relativize(hook));
            }
            if (!hookText.contains("Harness generated hook: " + name)) {
                failures.add(name + " hook must contain generated marker");
            }
            if (!hookText.contains("Harness stage: " + stage)) {
                failures.add(name + " hook must contain " + stage + " stage marker");
            }
            if (hookText.contains("packaged placeholder is replaced during harness installation")) {
                failures.add(name + " hook must be installer-generated selected-mode content");
            }
        }
        return hookText;
    }

    private static void validateHooks(Path root, List<String> failures) throws MojoExecutionException {
        String preCommitText = validateOneHook(root, "pre-commit", "compliance", failures);
        String prePushText = validateOneHook(root, "pre-push", "full-validation", failures);
        if (
            Pattern.compile("(^|\\s)(uv|bun|gradle|mvn)(\\s|$)|\\./gradlew|harnessValidate|harness_validate\\.py|harness-validate\\.ts")
                .matcher(preCommitText)
                .find()
        ) {
            failures.add("pre-commit hook must not run full stack validation commands");
        }
        String command = hookCommand(prePushText);
        if (command.isBlank()) {
            failures.add("pre-push hook must declare Harness validation command");
            return;
        }
        if (!EXPECTED_VALIDATION_COMMAND.equals(command)) {
            failures.add("pre-push hook declares unsupported validation command: " + command);
            return;
        }
        if (!List.of(prePushText.split("\\R")).contains(command)) {
            failures.add("pre-push hook must run the declared validation command");
        }
        for (String ciFile : List.of(".github/workflows/harness.yml", ".gitlab-ci.yml")) {
            Path path = root.resolve(ciFile);
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && isSafeRegularFile(root, path, failures)
                && !read(path).contains(command)) {
                failures.add(ciFile + ": CI command mismatch - expected " + command);
            }
        }
    }


    private static void validateEnvShebangs(Path root, List<String> failures) throws MojoExecutionException {
        for (String baseName : List.of(
            ".claude/harness",
            ".claude/skills"
        )) {
            Path base = root.resolve(baseName);
            if (!isSafeDirectory(root, base, failures)) {
                continue;
            }
            for (Path file : safeFiles(root, base, failures)) {
                if (!Files.isExecutable(file)) {
                    continue;
                }
                String first = firstLine(file);
                if (first.startsWith("#!") && !first.startsWith("#!/usr/bin/env ")) {
                    failures.add("executable script should use /usr/bin/env shebang: " + root.relativize(file));
                }
            }
        }
    }

    private static List<Path> safeFiles(Path root, Path base, List<String> failures) throws MojoExecutionException {
        if (!Files.exists(base, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if (Files.isSymbolicLink(base)) {
            failures.add("symlink scan root is not allowed: " + root.relativize(base));
            return List.of();
        }
        if (Files.isRegularFile(base, LinkOption.NOFOLLOW_LINKS)) {
            return List.of(base);
        }
        List<Path> output = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(base)) {
            for (Path candidateFile : stream.toList()) {
                if (candidateFile.equals(base)) {
                    continue;
                }
                if (Files.isSymbolicLink(candidateFile)) {
                    failures.add("symlink scan entry is not allowed: " + root.relativize(candidateFile));
                } else if (Files.isRegularFile(candidateFile, LinkOption.NOFOLLOW_LINKS)) {
                    output.add(candidateFile);
                }
            }
        } catch (IOException error) {
            throw new MojoExecutionException("failed to inspect " + base, error);
        }
        return output;
    }

    private static List<Path> safeFileOrWalk(
        Path root,
        Path base,
        List<String> failures
    ) throws MojoExecutionException {
        if (
            Files.isSymbolicLink(base)
                && allowedRootContractTarget(root, base) == null
        ) {
            failures.add("symlink path is not allowed: " + root.relativize(base));
            return List.of();
        }
        if (isSafeRegularFile(root, base, failures)) {
            return List.of(base);
        }
        return safeFiles(root, base, failures);
    }

    private static Path allowedRootContractTarget(Path root, Path path) {
        Path normalizedRoot = root.normalize();
        Path fileName = path.getFileName();
        if (
            fileName == null
                || path.getParent() == null
                || !path.getParent().normalize().equals(normalizedRoot)
        ) {
            return null;
        }
        String name = fileName.toString();
        if (!name.equals("AGENTS.md") && !name.equals("CLAUDE.md")) {
            return null;
        }
        String expected = name.equals("AGENTS.md") ? "CLAUDE.md" : "AGENTS.md";
        try {
            Path targetName = Files.readSymbolicLink(path);
            if (
                targetName.getNameCount() != 1
                    || !targetName.toString().equals(expected)
            ) {
                return null;
            }
            Path target = normalizedRoot.resolve(targetName).normalize();
            if (
                target.getParent().equals(normalizedRoot)
                    && !Files.isSymbolicLink(target)
                    && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
            ) {
                return target;
            }
            return null;
        } catch (IOException error) {
            return null;
        }
    }

    private static boolean isSafeRegularFile(Path root, Path path, List<String> failures) {
        if (Files.isSymbolicLink(path)) {
            if (allowedRootContractTarget(root, path) != null) {
                return true;
            }
            failures.add("symlink file is not allowed: " + root.relativize(path));
            return false;
        }
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isSafeDirectory(Path root, Path path, List<String> failures) {
        if (Files.isSymbolicLink(path)) {
            failures.add("symlink directory is not allowed: " + root.relativize(path));
            return false;
        }
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
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
                return Files.readAllLines(target, StandardCharsets.UTF_8)
                    .stream()
                    .findFirst()
                    .orElse("");
            }
            if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return Files.readAllLines(path, StandardCharsets.UTF_8)
                    .stream()
                    .findFirst()
                    .orElse("");
            }
            return "";
        } catch (IOException error) {
            return "";
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
