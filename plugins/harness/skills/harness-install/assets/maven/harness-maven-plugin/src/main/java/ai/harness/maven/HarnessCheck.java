package ai.harness.maven;

import tools.jackson.databind.JsonNode;
import org.apache.maven.plugin.MojoExecutionException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.regex.Pattern;
import java.util.Collectors;
import java.util.Collections;

/**
 * Enum of harness validation checks. Each value implements a validate method
 * that performs category-specific validation and returns a list of findings.
 */
enum HarnessCheck {
    REQUIRE_FILES_EXIST("requireFilesExist") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireFilesExist");
            List<String> paths = extractPaths(catNode.get("parameters").get("paths"));
            String severity = getSeverity(manifest, category());
            return paths.stream()
                    .filter(path -> !isSafeRegularFile(root, root.resolve(path)))
                    .map(path -> new Finding(severity, category(), "missing file: " + path))
                    .toList();
        }
    },

    REQUIRE_DIRECTORIES_EXIST("requireDirectoriesExist") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireDirectoriesExist");
            List<String> paths = extractPaths(catNode.get("parameters").get("paths"));
            String severity = getSeverity(manifest, category());
            return paths.stream()
                    .filter(path -> !isSafeDirectory(root, root.resolve(path)))
                    .map(path -> new Finding(severity, category(), "missing directory: " + path))
                    .toList();
        }
    },

    REQUIRE_KEEPFILE_IN_EMPTY_DIRECTORIES("requireKeepfileInEmptyDirectories") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireKeepfileInEmptyDirectories");
            List<String> directories = extractPaths(catNode.get("parameters").get("directories"));
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
    },

    REQUIRE_TEMPLATE_GROUPS("requireTemplateGroups") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireTemplateGroups");
            String targetRoot = catNode.get("parameters").get("targetRoot").asText();
            List<String> groups = extractPaths(catNode.get("parameters").get("groups"));
            String severity = getSeverity(manifest, category());
            return groups.stream()
                    .filter(g -> !isSafeDirectory(root, root.resolve(targetRoot).resolve(g)))
                    .map(g -> new Finding(severity, category(), "missing template group: " + targetRoot + "/" + g))
                    .toList();
        }
    },

    REQUIRE_DOC_HEADINGS("requireDocHeadings") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireDocHeadings");
            List<String> requiredFiles = extractPaths(manifest.get("requireFilesExist").get("parameters").get("paths"));
            List<String> headings = extractPaths(catNode.get("parameters").get("headings"));
            JsonNode sourceFilter = catNode.get("parameters").get("sourceFilter");
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
    },

    REQUIRE_DOC_CONTENT("requireDocContent") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireDocContent");
            JsonNode checksNode = catNode.get("parameters").get("checks");
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
    },

    REQUIRE_AGENT_FRONTMATTER("requireAgentFrontmatter") {
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
    },

    REQUIRE_SKILL_FRONTMATTER("requireSkillFrontmatter") {
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
    },

    FORBID_SCAFFOLD_LEAKS("forbidScaffoldLeaks") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("forbidScaffoldLeaks");
            JsonNode scope = catNode.get("parameters").get("scope");
            List<String> bases = extractPaths(scope.get("bases"));
            List<String> excluded = extractPaths(scope.get("excludedSubtrees"));
            List<String> extensions = extractPaths(scope.get("extensions"));
            JsonNode patternsNode = catNode.get("parameters").get("patterns");
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
    },

    REQUIRE_HOOK_SHEBANG("requireHookShebang") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookShebang");
            List<String> hooks = extractPaths(catNode.get("parameters").get("hooks"));
            String expected = catNode.get("parameters").get("expectedShebang").asText();
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
    },

    REQUIRE_HOOK_EXECUTABLE("requireHookExecutable") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookExecutable");
            List<String> hooks = extractPaths(catNode.get("parameters").get("hooks"));
            String severity = getSeverity(manifest, category());

            return hooks.stream()
                    .filter(hook -> isSafeRegularFile(root, root.resolve(hook)) && !Files.isExecutable(root.resolve(hook)))
                    .map(hook -> new Finding(severity, category(), hook + " must be executable"))
                    .toList();
        }
    },

    REQUIRE_HOOK_GENERATED_MARKER("requireHookGeneratedMarker") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookGeneratedMarker");
            List<String> hooks = extractPaths(catNode.get("parameters").get("hooks"));
            String markerTemplate = catNode.get("parameters").get("markerTemplate").asText();
            String placeholderForbidden = catNode.get("parameters").get("placeholderForbidden").asText();
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
    },

    REQUIRE_HOOK_STAGE("requireHookStage") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookStage");
            JsonNode stages = catNode.get("parameters").get("stages");
            JsonNode stackStages = stages.get("maven");
            if (stackStages == null) {
                return List.of();
            }

            String markerTemplate = catNode.get("parameters").get("markerTemplate").asText();
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
    },

    REQUIRE_HOOK_COMMAND("requireHookCommand") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireHookCommand");
            JsonNode allowedCmds = catNode.get("parameters").get("allowedCommands").get("maven");
            String expectedCmd = allowedCmds == null || allowedCmds.size() == 0 ? "" : allowedCmds.get(0).asText();
            String severity = getSeverity(manifest, category());

            String prePushPath = catNode.get("parameters").get("prePushHook").asText();
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

            String preCommitPath = catNode.get("parameters").get("preCommitHook").asText();
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
    },

    REQUIRE_CI_COMMAND_MATCHES_HOOK("requireCiCommandMatchesHook") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireCiCommandMatchesHook");
            String refHook = catNode.get("parameters").get("referenceHook").asText();
            Path refPath = root.resolve(refHook);

            if (!isSafeRegularFile(root, refPath)) {
                return List.of();
            }

            String refText = readFile(root, refPath);
            String expectedCmd = extractHookCommand(refText);
            if (expectedCmd.isEmpty()) {
                return List.of();
            }

            List<String> ciFiles = extractPaths(catNode.get("parameters").get("ciFiles"));
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
    },

    REQUIRE_ENV_SHEBANG_UNDER("requireEnvShebangUnder") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireEnvShebangUnder");
            List<String> directories = extractPaths(catNode.get("parameters").get("directories"));
            String expectedPrefix = catNode.get("parameters").get("expectedPrefix").asText();
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
    },

    FORBID_UNCHECKED_TASKS_UNDER("forbidUncheckedTasksUnder") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("forbidUncheckedTasksUnder");
            String directory = catNode.get("parameters").get("directory").asText();
            String uncheckedPattern = catNode.get("parameters").get("uncheckedTaskPattern").asText();
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
    },

    FORBID_UNSAFE_SYMLINKS("forbidUnsafeSymlinks") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("forbidUnsafeSymlinks");
            JsonNode allowedNode = catNode.get("parameters").get("allowedSymlinkPairs");
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
    },

    REQUIRE_SINGLE_TOP_LEVEL_KOTLIN_DECLARATION("requireSingleTopLevelKotlinDeclaration") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            JsonNode catNode = manifest.get("requireSingleTopLevelKotlinDeclaration");
            List<String> directories = extractPaths(catNode.get("parameters").get("directories"));
            String severity = getSeverity(manifest, category());

            return directories.stream()
                    .flatMap(dir -> {
                        try {
                            Path dirPath = root.resolve(dir);
                            return safeFileOrWalk(root, dirPath).stream()
                                    .filter(f -> f.getFileName().toString().endsWith(".kt"))
                                    .flatMap(file -> validateKotlinDeclarations(root, file, severity).stream());
                        } catch (MojoExecutionException e) {
                            return Stream.empty();
                        }
                    })
                    .toList();
        }

        private List<Finding> validateKotlinDeclarations(Path root, Path file, String severity) throws MojoExecutionException {
            String text = readFile(root, file);
            int count = 0;
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("class ") || trimmed.startsWith("fun ") || trimmed.startsWith("interface ") ||
                    trimmed.startsWith("object ") || trimmed.startsWith("enum class ")) {
                    count++;
                }
            }
            return count != 1
                    ? List.of(new Finding(severity, category(), "Kotlin file must have exactly 1 top-level declaration: " + root.relativize(file)))
                    : List.of();
        }
    },

    FORBID_GREATER_THAN_COMPARISON("forbidGreaterThanComparison") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            String severity = getSeverity(manifest, category());
            try {
                List<Path> sources = stackSources(manifest, category());
                return sources.stream()
                        .flatMap(file -> validateGreaterThanComparison(root, file, severity).stream())
                        .toList();
            } catch (IOException e) {
                return List.of(new Finding(severity, category(), "failed to enumerate Java sources: " + e.getMessage()));
            }
        }

        private List<Finding> validateGreaterThanComparison(Path root, Path file, String severity) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                List<Finding> findings = new java.util.ArrayList<>();
                cu.walk(BinaryExpr.class, expr -> {
                    if (expr.getOperator() == BinaryExpr.Operator.GREATER || expr.getOperator() == BinaryExpr.Operator.GREATER_EQUALS) {
                        int line = expr.getBegin().map(p -> p.line).orElse(-1);
                        String op = expr.getOperator() == BinaryExpr.Operator.GREATER ? ">" : ">=";
                        String replacement = expr.getOperator() == BinaryExpr.Operator.GREATER ? "<" : "<=";
                        findings.add(new Finding(severity, category(), root.relativize(file) + ":" + line + ": forbidden `" + op + "`; use `" + replacement + "`"));
                    }
                });
                return findings;
            } catch (IOException e) {
                return List.of(new Finding(severity, category(), "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
            }
        }
    },

    FORBID_BLANK_LINE_IN_LEAF_FUNCTION("forbidBlankLineInLeafFunction") {
        @Override
        public List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException {
            String severity = getSeverity(manifest, category());
            try {
                List<Path> sources = stackSources(manifest, category());
                return sources.stream()
                        .flatMap(file -> validateBlankLinesInLeafFunctions(root, file, severity).stream())
                        .toList();
            } catch (IOException e) {
                return List.of(new Finding(severity, category(), "failed to enumerate Java sources: " + e.getMessage()));
            }
        }

        private List<Finding> validateBlankLinesInLeafFunctions(Path root, Path file, String severity) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(file);
                List<String> sourceLines = Files.readAllLines(file);
                List<Finding> findings = new java.util.ArrayList<>();
                cu.walk(MethodDeclaration.class, method -> {
                    if (isLeafMethod(method)) {
                        method.getBody().ifPresent(body -> {
                            body.getBegin().ifPresent(beginPos -> {
                                body.getEnd().ifPresent(endPos -> {
                                    int startLine = beginPos.line - 1;
                                    int endLine = endPos.line;
                                    for (int i = startLine; i < endLine && i < sourceLines.size(); i++) {
                                        if (sourceLines.get(i).trim().isEmpty()) {
                                            findings.add(new Finding(severity, category(), root.relativize(file) + ":" + (i + 1) + ": blank line in leaf function"));
                                        }
                                    }
                                });
                            });
                        });
                    }
                });
                return findings;
            } catch (IOException e) {
                return List.of(new Finding(severity, category(), "failed to parse " + root.relativize(file) + ": " + e.getMessage()));
            }
        }

        private boolean isLeafMethod(MethodDeclaration method) {
            return method.getBody()
                    .map(body -> {
                        java.util.List<MethodDeclaration> nestedMethods = new java.util.ArrayList<>();
                        java.util.List<com.github.javaparser.ast.expr.LambdaExpr> lambdas = new java.util.ArrayList<>();
                        body.walk(MethodDeclaration.class, nestedMethods::add);
                        body.walk(com.github.javaparser.ast.expr.LambdaExpr.class, lambdas::add);
                        return nestedMethods.isEmpty() && lambdas.isEmpty();
                    })
                    .orElse(false);
        }
    };

    private final String category;

    HarnessCheck(String category) {
        this.category = category;
    }

    public String category() {
        return category;
    }

    public boolean applies(JsonNode manifest) {
        JsonNode node = manifest.get(category);
        if (node == null || !node.isObject()) {
            return false;
        }
        JsonNode enabled = node.get("enabled");
        return enabled == null || !enabled.isBoolean() || enabled.asBoolean();
    }

    public abstract List<Finding> validate(Path root, JsonNode manifest) throws MojoExecutionException;

    protected static String getSeverity(JsonNode manifest, String category) {
        JsonNode catNode = manifest.get(category);
        if (catNode != null && catNode.has("severity")) {
            String sev = catNode.get("severity").asText();
            if ("WARN".equals(sev) || "INFO".equals(sev)) {
                return sev;
            }
        }
        return "ERROR";
    }

    protected static String readFile(Path root, Path path) throws MojoExecutionException {
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

    protected static boolean isSafeRegularFile(Path root, Path path) {
        if (Files.isSymbolicLink(path)) {
            return allowedRootContractTarget(root, path) != null;
        }
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    protected static boolean isSafeDirectory(Path root, Path path) {
        if (Files.isSymbolicLink(path)) {
            return false;
        }
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS);
    }

    protected static List<Path> safeFileOrWalk(Path root, Path base) throws MojoExecutionException {
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

    protected static Path allowedRootContractTarget(Path root, Path path) {
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

    protected static List<String> extractPaths(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return Stream.of(node.spliterator(), false).map(JsonNode::asText).toList();
    }

    protected static List<Path> stackSources(JsonNode manifest, String category) throws IOException {
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

    protected static List<Path> walkRoot(FileSystem fs, Path base, String rootEntry, Set<String> extensions) {
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

    protected static String extensionOf(Path p) {
        String name = p.getFileName().toString();
        int idx = name.lastIndexOf('.');
        return idx < 0 ? "" : name.substring(idx + 1);
    }

    protected static boolean containsSegment(Path p, String segment) {
        for (int i = 0; i < p.getNameCount(); i++) {
            if (p.getName(i).toString().equals(segment)) {
                return true;
            }
        }
        return false;
    }
}
