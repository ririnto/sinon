#!/usr/bin/env bun
import { lstatSync, readdirSync, readFileSync, readlinkSync, statSync } from "node:fs";
import { dirname, join } from "node:path";

const root = process.cwd();
const STACK = "bun" as const;
const MANIFEST_PATH = "docs/harness/manifest.json";

// Helper functions
function pathOf(path: string): string {
  return join(root, path);
}

function read(path: string): string {
  try {
    const target = allowedRootContractTarget(path);
    return readFileSync(target ?? pathOf(path), "utf8");
  } catch {
    return "";
  }
}

function firstLine(path: string): string {
  return read(path).split(/\r?\n/, 1)[0] ?? "";
}

function isFile(path: string): boolean {
  try {
    if (isSymlink(path) && allowedRootContractTarget(path) === null) {
      return false;
    }
    return statSync(pathOf(path)).isFile();
  } catch {
    return false;
  }
}

function isDirectory(path: string): boolean {
  try {
    if (isSymlink(path)) {
      return false;
    }
    return statSync(pathOf(path)).isDirectory();
  } catch {
    return false;
  }
}

function isExecutablePath(path: string): boolean {
  try {
    const target = allowedRootContractTarget(path);
    return (statSync(target ?? pathOf(path)).mode & 0o100) !== 0;
  } catch {
    return false;
  }
}

function isSymlink(path: string): boolean {
  try {
    return lstatSync(pathOf(path)).isSymbolicLink();
  } catch {
    return false;
  }
}

function allowedRootContractTarget(path: string): string | null {
  if (path !== "AGENTS.md" && path !== "CLAUDE.md") {
    return null;
  }
  try {
    const expected = path === "AGENTS.md" ? "CLAUDE.md" : "AGENTS.md";
    if (readlinkSync(pathOf(path)) !== expected) {
      return null;
    }
    return !lstatSync(pathOf(expected)).isSymbolicLink() && statSync(pathOf(expected)).isFile()
      ? pathOf(expected)
      : null;
  } catch {
    return null;
  }
}

function isSafeFile(path: string): readonly [boolean, readonly string[]] {
  const warnings: string[] = [];
  if (isSymlink(path)) {
    if (allowedRootContractTarget(path) === null) {
      warnings.push(`symlink file is not allowed: ${path}`);
      return [false, warnings];
    }
    return [true, warnings];
  }
  return [isFile(path), warnings];
}

function isSafeDirectory(path: string): readonly [boolean, readonly string[]] {
  const warnings: string[] = [];
  if (isSymlink(path)) {
    warnings.push(`symlink directory is not allowed: ${path}`);
    return [false, warnings];
  }
  return [isDirectory(path), warnings];
}

function walk(path: string): readonly [readonly string[], readonly string[]] {
  const warnings: string[] = [];
  if (isSymlink(path)) {
    warnings.push(`symlink scan root is not allowed: ${path}`);
    return [[], warnings];
  }
  if (isFile(path)) {
    return [[path], warnings];
  }
  if (!isDirectory(path)) {
    return [[], warnings];
  }
  const files: string[] = [];
  for (const entry of readdirSync(pathOf(path))) {
    const child = `${path}/${entry}`;
    const full = pathOf(child);
    if (lstatSync(full).isSymbolicLink()) {
      warnings.push(`symlink scan entry is not allowed: ${child}`);
      continue;
    }
    if (statSync(full).isDirectory()) {
      const [subFiles, subWarnings] = walk(child);
      files.push(...subFiles);
      warnings.push(...subWarnings);
    }
    if (statSync(full).isFile()) {
      files.push(child);
    }
  }
  return [files, warnings];
}

function safeFileOrWalk(path: string): readonly [readonly string[], readonly string[]] {
  const warnings: string[] = [];
  if (isSymlink(path) && allowedRootContractTarget(path) === null) {
    warnings.push(`symlink path is not allowed: ${path}`);
    return [[], warnings];
  }
  const [isSafe] = isSafeFile(path);
  if (isSafe) {
    return [[path], warnings];
  }
  return walk(path);
}

function manifestArray(value: unknown): readonly string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function manifestObject(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function loadManifest(): Record<string, unknown> {
  if (isSymlink(MANIFEST_PATH)) {
    return {};
  }
  try {
    return JSON.parse(readFileSync(pathOf(MANIFEST_PATH), "utf8"));
  } catch {
    return {};
  }
}

// Validation functions
function validateStructure(manifest: Record<string, unknown>): readonly string[] {
  const failures: string[] = [];

  const requiredFiles = manifestArray(manifest.requiredFiles);
  for (const path of requiredFiles) {
    const [safe, warnings] = isSafeFile(path);
    failures.push(...warnings);
    if (!safe) {
      failures.push(`missing file: ${path}`);
    }
  }

  const requiredDirectories = manifestArray(manifest.requiredDirectories);
  for (const path of requiredDirectories) {
    const [safe, warnings] = isSafeDirectory(path);
    failures.push(...warnings);
    if (!safe) {
      failures.push(`missing directory: ${path}`);
    }
  }

  const emptyDirectoryKeepFiles = manifestArray(manifest.emptyDirectoryKeepFiles);
  for (const keep of emptyDirectoryKeepFiles) {
    const directory = dirname(keep);
    const [dirSafe] = isSafeDirectory(directory);
    if (!dirSafe) {
      continue;
    }
    const realFiles = readdirSync(pathOf(directory)).filter((entry) => entry !== ".gitkeep");
    const [keepSafe] = isSafeFile(keep);
    if (realFiles.length === 0 && !keepSafe) {
      failures.push(`empty directory must keep placeholder or real files: ${directory}`);
    }
  }

  return failures;
}

function validateDocsHeadings(manifest: Record<string, unknown>): readonly string[] {
  const failures: string[] = [];
  const requiredFiles = manifestArray(manifest.requiredFiles);
  const requiredDocHeadings = manifestArray(manifest.requiredDocHeadings);

  const requiredAuthoredDocs = requiredFiles.filter(
    (path) => path.startsWith("docs/") && path.endsWith(".md")
  );

  for (const doc of requiredAuthoredDocs) {
    const [docExists] = isSafeFile(doc);
    if (!docExists) {
      continue;
    }
    const text = read(doc);
    for (const heading of requiredDocHeadings) {
      if (!text.includes(heading)) {
        failures.push(`doc missing ${heading}: ${doc}`);
      }
    }
  }

  return failures;
}

function validateContentChecks(manifest: Record<string, unknown>): readonly string[] {
  const failures: string[] = [];
  const checks = manifest.requiredContentChecks;

  if (!Array.isArray(checks)) {
    return failures;
  }

  for (const check of checks) {
    if (typeof check !== "object" || check === null) {
      continue;
    }
    const checkObj = check as Record<string, unknown>;
    const files = manifestArray(checkObj.files);
    const containsAll = manifestArray(checkObj.containsAll);
    const failureMessage = typeof checkObj.failureMessage === "string" ? checkObj.failureMessage : "";

    const combinedText = files.map((f) => read(f)).join("\n");
    const hasMissing = containsAll.some((substring) => !combinedText.includes(substring));

    if (hasMissing && failureMessage) {
      failures.push(failureMessage);
    }
  }

  return failures;
}

function validateAgents(): readonly string[] {
  const failures: string[] = [];
  const [agents, agentWarnings] = walk(".claude/agents");
  failures.push(...agentWarnings);

  const agentFiles = (agents as readonly string[]).filter(
    (file) => dirname(file) === ".claude/agents" && file.endsWith(".md")
  );

  if (agentFiles.length === 0) {
    failures.push(".claude/agents must contain at least one .md agent");
  }

  for (const agent of agentFiles) {
    const text = read(agent);
    if (!text.startsWith("---")) {
      failures.push(`agent missing frontmatter: ${agent}`);
    }
    if (!/^name:\s*[-a-z0-9]+\s*$/m.test(text)) {
      failures.push(`agent missing name: ${agent}`);
    }
    if (!/^description:\s*.+$/m.test(text)) {
      failures.push(`agent missing description: ${agent}`);
    }
  }

  return failures;
}

function validateSkills(): readonly string[] {
  const failures: string[] = [];
  const [skills, skillWarnings] = walk(".claude/skills");
  failures.push(...skillWarnings);

  const skillFiles = (skills as readonly string[]).filter((file) => file.endsWith("/SKILL.md"));

  if (skillFiles.length === 0) {
    failures.push(".claude/skills must contain at least one SKILL.md");
  }

  for (const skill of skillFiles) {
    const text = read(skill);
    if (!text.startsWith("---")) {
      failures.push(`skill missing frontmatter: ${skill}`);
    }
    if (!/^description:\s*.+$/m.test(text)) {
      failures.push(`skill missing description: ${skill}`);
    }
  }

  return failures;
}

function validateActiveAssets(manifest: Record<string, unknown>): readonly string[] {
  const failures: string[] = [];
  const activeAssetBases = manifestArray(manifest.activeAssetBases);
  const excludedActiveAssetSubtrees = manifestArray(manifest.excludedActiveAssetSubtrees);
  const activeAssetExtensions = manifestArray(manifest.activeAssetExtensions);
  const leakPatternsRaw = manifest.leakPatterns;

  const leakPatterns: Array<readonly [RegExp, string]> = [];
  if (Array.isArray(leakPatternsRaw)) {
    for (const item of leakPatternsRaw) {
      if (typeof item === "object" && item !== null) {
        const obj = item as Record<string, unknown>;
        const pattern = typeof obj.pattern === "string" ? obj.pattern : "";
        const label = typeof obj.label === "string" ? obj.label : "";
        if (pattern && label) {
          try {
            leakPatterns.push([new RegExp(pattern), label]);
          } catch {
            // skip invalid regex
          }
        }
      }
    }
  }

  for (const base of activeAssetBases) {
    const [files, warnings] = safeFileOrWalk(base);
    failures.push(...warnings);
    for (const file of files) {
      let excluded = false;
      for (const subtree of excludedActiveAssetSubtrees) {
        if (file === subtree || file.startsWith(`${subtree}/`)) {
          excluded = true;
          break;
        }
      }
      if (excluded) {
        continue;
      }

      const extMatch = /\.([a-z0-9]+)$/.exec(file);
      const ext = extMatch ? extMatch[1] : "";
      if (!activeAssetExtensions.includes(ext)) {
        continue;
      }

      const text = read(file);
      for (const [pattern, label] of leakPatterns) {
        if (pattern.test(text)) {
          failures.push(`${label} in active asset: ${file}`);
        }
      }
    }
  }

  return failures;
}

function hookCommand(prePushText: string): string {
  for (const line of prePushText.split(/\r?\n/)) {
    if (line.startsWith("# Harness validation command: ")) {
      return line.replace("# Harness validation command: ", "").trim();
    }
  }
  return "";
}

function validateOneHook(name: string, stage: string): readonly [string, readonly string[]] {
  const failures: string[] = [];
  const hook = `docs/harness/git-hooks/${name}`;
  let hookText = "";

  const [hookExists, safeWarnings] = isSafeFile(hook);
  failures.push(...safeWarnings);

  if (hookExists) {
    hookText = read(hook);
    if (firstLine(hook) !== "#!/usr/bin/env sh") {
      failures.push(`${name} hook must use #!/usr/bin/env sh`);
    }
    if (!isExecutablePath(hook)) {
      failures.push(`${name} hook must be executable: ${hook}`);
    }
    if (!hookText.includes(`Harness generated hook: ${name}`)) {
      failures.push(`${name} hook must contain generated marker`);
    }
    if (!hookText.includes(`Harness stage: ${stage}`)) {
      failures.push(`${name} hook must contain ${stage} stage marker`);
    }
    if (hookText.includes("packaged placeholder is replaced during harness installation")) {
      failures.push(`${name} hook must be installer-generated selected-mode content`);
    }
  }

  return [hookText, failures];
}

function validateHooks(manifest: Record<string, unknown>): readonly string[] {
  const failures: string[] = [];
  const expectedValidationCommands = manifestObject(manifest.expectedValidationCommands);
  const hookStages = manifestObject(manifest.hookStages);
  const stackHookStages = manifestObject(hookStages[STACK]);

  const preCommitStage = typeof stackHookStages.preCommit === "string" ? stackHookStages.preCommit : "compliance";
  const prePushStage = typeof stackHookStages.prePush === "string" ? stackHookStages.prePush : "full-validation";
  const expectedValidationCommand = typeof expectedValidationCommands[STACK] === "string"
    ? expectedValidationCommands[STACK]
    : "";

  const [preCommitText, preCommitFailures] = validateOneHook("pre-commit", preCommitStage);
  failures.push(...preCommitFailures);

  const [prePushText, prePushFailures] = validateOneHook("pre-push", prePushStage);
  failures.push(...prePushFailures);

  if (/(^|\s)(uv|bun|gradle|mvn)(\s|$)|\.\/gradlew|harnessValidate|harness_validate\.py|harness-validate\.ts/.test(preCommitText)) {
    failures.push("pre-commit hook must not run full stack validation commands");
  }

  const validationCommand = hookCommand(prePushText);
  if (validationCommand.length === 0) {
    failures.push("pre-push hook must declare Harness validation command");
  } else {
    if (expectedValidationCommand && validationCommand !== expectedValidationCommand) {
      failures.push(`pre-push hook declares unsupported validation command: ${validationCommand}`);
    } else {
      if (!prePushText.split(/\r?\n/).includes(validationCommand)) {
        failures.push("pre-push hook must run the declared validation command");
      }
      for (const ciFile of [".github/workflows/harness.yml", ".gitlab-ci.yml"]) {
        if (isFile(ciFile)) {
          const [ciSafe, ciWarnings] = isSafeFile(ciFile);
          failures.push(...ciWarnings);
          if (ciSafe && !read(ciFile).includes(validationCommand)) {
            failures.push(`${ciFile}: CI command mismatch - expected ${validationCommand}`);
          }
        }
      }
    }
  }

  return failures;
}

function validateEnvShebangs(manifest: Record<string, unknown>): readonly string[] {
  const failures: string[] = [];
  const envShebangBases = manifestArray(manifest.envShebangBases);

  for (const base of envShebangBases) {
    const [files, warnings] = walk(base);
    failures.push(...warnings);
    for (const file of files) {
      if (!isExecutablePath(file)) {
        continue;
      }
      if (firstLine(file).startsWith("#!") && !firstLine(file).startsWith("#!/usr/bin/env ")) {
        failures.push(`executable script should use /usr/bin/env shebang: ${file}`);
      }
    }
  }

  return failures;
}

function validateCompletedPlans(manifest: Record<string, unknown>): readonly string[] {
  const failures: string[] = [];
  const completedPlanDirectory = typeof manifest.completedPlanDirectory === "string"
    ? manifest.completedPlanDirectory
    : "docs/exec-plans/completed";
  const unfinishedTaskPatternStr = typeof manifest.unfinishedTaskPattern === "string"
    ? manifest.unfinishedTaskPattern
    : "";

  if (!unfinishedTaskPatternStr) {
    return failures;
  }

  let unfinishedTaskPattern: RegExp;
  try {
    unfinishedTaskPattern = new RegExp(unfinishedTaskPatternStr);
  } catch {
    return failures;
  }

  const [files, warnings] = walk(completedPlanDirectory);
  failures.push(...warnings);

  for (const file of files) {
    if (!file.endsWith(".md")) {
      continue;
    }
    const text = read(file);
    if (unfinishedTaskPattern.test(text)) {
      failures.push(`completed plan has unchecked tasks: ${file}`);
    }
  }

  return failures;
}

// Main
const manifest = loadManifest();
if (!manifest || typeof manifest !== "object" || Object.keys(manifest).length === 0) {
  console.error("Harness validation failed:");
  console.error(`- manifest not found or invalid: ${MANIFEST_PATH}`);
  process.exit(1);
}

const allFailures: string[] = [
  ...validateStructure(manifest),
  ...validateDocsHeadings(manifest),
  ...validateContentChecks(manifest),
  ...validateAgents(),
  ...validateSkills(),
  ...validateActiveAssets(manifest),
  ...validateHooks(manifest),
  ...validateEnvShebangs(manifest),
  ...validateCompletedPlans(manifest),
];

const uniqueFailures = new Set(allFailures);
if (uniqueFailures.size > 0) {
  console.error("Harness validation failed:");
  for (const failure of uniqueFailures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}
console.log("Harness validation passed");
