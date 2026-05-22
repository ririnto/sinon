#!/usr/bin/env bun
import { lstatSync, readdirSync, readFileSync, readlinkSync, statSync } from "node:fs";
import { dirname, join } from "node:path";

const root = process.cwd();
const expectedValidationCommand = "bun run docs/harness/bun/harness-validate.ts" as const;
const requiredFiles = [
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
  "docs/harness/git-hooks/pre-push",
] as const;
const requiredDirectories = [
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
  ".claude/skills",
] as const;
const emptyDirectoryKeepFiles = [
  "docs/exec-plans/active/.gitkeep",
  "docs/exec-plans/completed/.gitkeep",
  "docs/generated/.gitkeep",
] as const;
const optionalSeedFiles = ["docs/product-specs/new-user-onboarding.md"] as const;
const templateGroups = ["agent", "skill", "workflow", "ci", "docs"] as const;
const requiredDocHeadings = ["## Purpose", "## When To Update", "## Required Evidence"] as const;
const requiredAuthoredDocs: readonly string[] = (requiredFiles as readonly string[]).filter(
  (path) => path.startsWith("docs/") && path.endsWith(".md")
);
const leakPatterns: ReadonlyArray<readonly [RegExp, string]> = [
  [/\{\{/, "unresolved template token"],
  [/^name:\s*example-/m, "example frontmatter name"],
  [/Describe /, "scaffold prompt text"],
  [/\bTODO\b|\bTBD\b/, "TODO/TBD placeholder"],
  [/replace-with-stack-specific/, "stack placeholder"],
];

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

function manifestList(manifest: Record<string, unknown>, key: string): readonly string[] {
  const value = manifest[key];
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function loadManifest(): Record<string, unknown> {
  if (isSymlink("docs/harness/manifest.json")) {
    return {};
  }
  try {
    return JSON.parse(readFileSync(pathOf("docs/harness/manifest.json"), "utf8"));
  } catch {
    return {};
  }
}

// Validation functions
function validateManifestParity(): readonly string[] {
  const manifest = loadManifest();
  const failures: string[] = [];

  const compareList = (key: string, expected: readonly string[]): void => {
    const actual = manifestList(manifest, key).sort();
    const wanted = [...expected].sort();
    if (JSON.stringify(actual) !== JSON.stringify(wanted)) {
      failures.push(`manifest ${key} must match validator constants`);
    }
  };

  compareList("requiredFiles", requiredFiles as readonly string[]);
  compareList("requiredDirectories", requiredDirectories as readonly string[]);
  compareList("emptyDirectoryKeepFiles", emptyDirectoryKeepFiles as readonly string[]);
  compareList("optionalSeedFiles", optionalSeedFiles as readonly string[]);
  compareList("templateGroups", templateGroups as readonly string[]);

  return failures;
}

function validateStructure(): readonly string[] {
  const failures: string[] = [];

  for (const path of requiredFiles as readonly string[]) {
    const [safe, warnings] = isSafeFile(path);
    failures.push(...warnings);
    if (!safe) {
      failures.push(`missing file: ${path}`);
    }
  }

  for (const path of requiredDirectories as readonly string[]) {
    const [safe, warnings] = isSafeDirectory(path);
    failures.push(...warnings);
    if (!safe) {
      failures.push(`missing directory: ${path}`);
    }
  }

  for (const keep of emptyDirectoryKeepFiles as readonly string[]) {
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

function validateDocs(): readonly string[] {
  const failures: string[] = [];

  for (const doc of requiredAuthoredDocs) {
    const [docExists] = isSafeFile(doc);
    if (!docExists) {
      continue;
    }
    const text = read(doc);
    for (const heading of requiredDocHeadings as readonly string[]) {
      if (!text.includes(heading)) {
        failures.push(`doc missing ${heading}: ${doc}`);
      }
    }
  }

  return failures;
}

function validateContent(): readonly string[] {
  const failures: string[] = [];
  const agentsText = read("AGENTS.md");
  const claudeText = read("CLAUDE.md");
  const generatedText = [agentsText, claudeText, read("ARCHITECTURE.md")].join("\n");
  const evolutionText = [agentsText, claudeText, read("docs/harness/evolution-log.md")].join("\n");

  if (!agentsText.includes("Repository Harness Contract")) {
    failures.push("AGENTS.md must contain Repository Harness Contract");
  }
  if (!claudeText.includes("## Entry Point")) {
    failures.push("CLAUDE.md must contain an Entry Point section");
  }
  if (!claudeText.includes("AGENTS.md")) {
    failures.push("CLAUDE.md must reference AGENTS.md");
  }
  if (!agentsText.includes("docs/generated/")) {
    failures.push("AGENTS.md must describe docs/generated/ semantics");
  }
  if (!generatedText.includes("docs/generated/db-schema.md")) {
    failures.push(
      "repository docs must state that docs/generated/db-schema.md is only an example, not a required scaffold file"
    );
  }
  if (!generatedText.includes("source command") || !generatedText.includes("regeneration trigger")) {
    failures.push(
      "repository docs must describe generated-artifact source command and regeneration trigger metadata"
    );
  }
  if (!evolutionText.includes("discovery") || !evolutionText.includes("maintenance")) {
    failures.push("repository docs must state that the harness may evolve across development phases");
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

function validateTemplates(): readonly string[] {
  const failures: string[] = [];

  for (const group of templateGroups as readonly string[]) {
    const [dirSafe] = isSafeDirectory(`docs/harness/templates/${group}`);
    if (!dirSafe) {
      failures.push(`missing template group: docs/harness/templates/${group}`);
    }
  }

  return failures;
}

function validateActiveAssets(): readonly string[] {
  const failures: string[] = [];
  const bases = ["AGENTS.md", "CLAUDE.md", "ARCHITECTURE.md", "docs", ".claude/agents", ".claude/skills", "docs/harness", ".github"] as const;

  for (const base of bases) {
    const [files, warnings] = safeFileOrWalk(base);
    failures.push(...warnings);
    for (const file of files) {
      if (
        file.startsWith("docs/harness/templates/") ||
        !/\.(md|txt|json|ya?ml)$/.test(file)
      ) {
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

function validateHooks(): readonly string[] {
  const failures: string[] = [];
  const [preCommitText, preCommitFailures] = validateOneHook("pre-commit", "compliance");
  failures.push(...preCommitFailures);

  const [prePushText, prePushFailures] = validateOneHook("pre-push", "full-validation");
  failures.push(...prePushFailures);

  if (/(^|\s)(uv|bun|gradle|mvn)(\s|$)|\.\/gradlew|harnessValidate|harness_validate\.py|harness-validate\.ts/.test(preCommitText)) {
    failures.push("pre-commit hook must not run full stack validation commands");
  }

  const validationCommand = hookCommand(prePushText);
  if (validationCommand.length === 0) {
    failures.push("pre-push hook must declare Harness validation command");
  } else {
    if (validationCommand !== expectedValidationCommand) {
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

function validateEnvShebangs(): readonly string[] {
  const failures: string[] = [];
  const bases = ["docs/harness", ".claude/skills"] as const;

  for (const base of bases) {
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

function validateCompletedPlans(): readonly string[] {
  const failures: string[] = [];
  const [files, warnings] = walk("docs/exec-plans/completed");
  failures.push(...warnings);

  for (const file of files) {
    if (!file.endsWith(".md")) {
      continue;
    }
    const text = read(file);
    if (/^\s*-\s*\[ \]\s/m.test(text)) {
      failures.push(`completed plan has unchecked tasks: ${file}`);
    }
  }

  return failures;
}

// Main
const allFailures: string[] = [
  ...validateManifestParity(),
  ...validateStructure(),
  ...validateDocs(),
  ...validateContent(),
  ...validateAgents(),
  ...validateSkills(),
  ...validateTemplates(),
  ...validateActiveAssets(),
  ...validateHooks(),
  ...validateEnvShebangs(),
  ...validateCompletedPlans(),
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
