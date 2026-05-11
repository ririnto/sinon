#!/usr/bin/env bun
import { lstatSync, readdirSync, readFileSync, readlinkSync, statSync } from "node:fs";
import { dirname, join } from "node:path";

const root = process.cwd();
const failures: string[] = [];
const requiredFiles = ["AGENTS.md", "ARCHITECTURE.md", "CLAUDE.md", "docs/design-docs/index.md", "docs/design-docs/core-beliefs.md", "docs/exec-plans/tech-debt-tracker.md", "docs/product-specs/index.md", "docs/DESIGN.md", "docs/FRONTEND.md", "docs/PLANS.md", "docs/PRODUCT_SENSE.md", "docs/QUALITY_SCORE.md", "docs/RELIABILITY.md", "docs/SECURITY.md", ".claude/harness/git-hooks/pre-commit"];
const requiredDirectories = ["docs", "docs/design-docs", "docs/exec-plans", "docs/exec-plans/active", "docs/exec-plans/completed", "docs/generated", "docs/product-specs", "docs/references", ".claude/agents", ".claude/skills", ".claude/harness/templates"];
const emptyDirectoryKeepFiles = ["docs/exec-plans/active/.gitkeep", "docs/exec-plans/completed/.gitkeep", "docs/generated/.gitkeep"];
const optionalSeedFiles = ["docs/product-specs/new-user-onboarding.md", "docs/references/design-system-reference-llms.txt", "docs/references/nixpacks-llms.txt", "docs/references/uv-llms.txt"];
const templateGroups = ["agent", "skill", "workflow", "ci", "docs"];
const requiredDocHeadings = ["## Purpose", "## When To Update", "## Required Evidence", "## Validation Link"];
const requiredAuthoredDocs = requiredFiles.filter((path) => path.startsWith("docs/") && path.endsWith(".md"));
const leakPatterns: [RegExp, string][] = [
  [/\{\{/, "unresolved template token"],
  [/^name:\s*example-/m, "example frontmatter name"],
  [/Describe /, "scaffold prompt text"],
  [/\bTODO\b|\bTBD\b/, "TODO/TBD placeholder"],
  [/replace-with-stack-specific/, "stack placeholder"],
];

function pathOf(path: string): string { return join(root, path); }
function read(path: string): string {
  try {
    const target = allowedRootContractTarget(path);
    return readFileSync(target ?? pathOf(path), "utf8");
  } catch { return ""; }
}
function firstLine(path: string): string { return read(path).split(/\r?\n/, 1)[0] ?? ""; }
function isFile(path: string): boolean {
  try {
    if (isSymlink(path) && allowedRootContractTarget(path) === null) {
      return false;
    }
    return statSync(pathOf(path)).isFile();
  } catch { return false; }
}
function isDirectory(path: string): boolean {
  try {
    if (isSymlink(path)) {
      return false;
    }
    return statSync(pathOf(path)).isDirectory();
  } catch { return false; }
}
function isExecutablePath(path: string): boolean { try { const target = allowedRootContractTarget(path); return (statSync(target ?? pathOf(path)).mode & 0o100) !== 0; } catch { return false; } }
function isSymlink(path: string): boolean { try { return lstatSync(pathOf(path)).isSymbolicLink(); } catch { return false; } }
function allowedRootContractTarget(path: string): string | null {
  if (path !== "AGENTS.md" && path !== "CLAUDE.md") {
    return null;
  }
  try {
    const expected = path === "AGENTS.md" ? "CLAUDE.md" : "AGENTS.md";
    if (readlinkSync(pathOf(path)) !== expected) {
      return null;
    }
    return !lstatSync(pathOf(expected)).isSymbolicLink() && statSync(pathOf(expected)).isFile() ? pathOf(expected) : null;
  } catch { return null; }
}
function isSafeFile(path: string): boolean {
  if (isSymlink(path)) {
    if (allowedRootContractTarget(path) !== null) {
      return true;
    }
    failures.push(`symlink file is not allowed: ${path}`);
    return false;
  }
  return isFile(path);
}
function isSafeDirectory(path: string): boolean {
  if (isSymlink(path)) {
    failures.push(`symlink directory is not allowed: ${path}`);
    return false;
  }
  return isDirectory(path);
}
function walk(path: string): string[] {
  if (isSymlink(path)) {
    failures.push(`symlink scan root is not allowed: ${path}`);
    return [];
  }
  if (isFile(path)) {
    return [path];
  }
  if (!isDirectory(path)) {
    return [];
  }
  const output: string[] = [];
  for (const entry of readdirSync(pathOf(path))) {
    const child = `${path}/${entry}`;
    const full = pathOf(child);
    if (lstatSync(full).isSymbolicLink()) {
      failures.push(`symlink scan entry is not allowed: ${child}`);
      continue;
    }
    if (statSync(full).isDirectory()) {
      output.push(...walk(child));
    }
    if (statSync(full).isFile()) {
      output.push(child);
    }
  }
  return output;
}
function safeFileOrWalk(path: string): string[] {
  if (isSymlink(path) && allowedRootContractTarget(path) === null) {
    failures.push(`symlink path is not allowed: ${path}`);
    return [];
  }
  return isSafeFile(path) ? [path] : walk(path);
}
function manifestList(manifest: Record<string, unknown>, key: string): string[] {
  const value = manifest[key];
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}
function compareManifestList(manifest: Record<string, unknown>, key: string, expected: string[]): void {
  const actual = manifestList(manifest, key).sort();
  const wanted = [...expected].sort();
  if (JSON.stringify(actual) !== JSON.stringify(wanted)) {
    failures.push(`manifest ${key} must match validator constants`);
  }
}
function loadManifest(): Record<string, unknown> {
  if (isSymlink(".claude/harness/manifest.json")) {
    failures.push("symlink file is not allowed: .claude/harness/manifest.json");
    return {};
  }
  try { return JSON.parse(readFileSync(pathOf(".claude/harness/manifest.json"), "utf8")); }
  catch (error) { failures.push(`invalid or missing manifest: .claude/harness/manifest.json: ${error}`); return {}; }
}
function contentFailures(agentsText: string, claudeText: string, generatedText: string, evolutionText: string): string[] {
  return [
    ...(!agentsText.includes("Repository Harness Contract") ? ["AGENTS.md must contain Repository Harness Contract"] : []),
    ...(!claudeText.includes("Claude Code Entry Point") ? ["CLAUDE.md must contain Claude Code Entry Point"] : []),
    ...(!claudeText.includes("AGENTS.md") ? ["CLAUDE.md must reference AGENTS.md"] : []),
    ...(!agentsText.includes("docs/generated/") ? ["AGENTS.md must describe docs/generated/ semantics"] : []),
    ...(!generatedText.includes("docs/generated/db-schema.md") ? ["repository docs must state that docs/generated/db-schema.md is only an example, not a required scaffold file"] : []),
    ...(!generatedText.includes("source command") || !generatedText.includes("regeneration trigger") ? ["repository docs must describe generated-artifact source command and regeneration trigger metadata"] : []),
    ...(!evolutionText.includes("discovery") || !evolutionText.includes("maintenance") ? ["repository docs must state that the harness may evolve across development phases"] : []),
  ];
}

const manifest = loadManifest();
compareManifestList(manifest, "requiredFiles", requiredFiles);
compareManifestList(manifest, "requiredDirectories", requiredDirectories);
compareManifestList(manifest, "emptyDirectoryKeepFiles", emptyDirectoryKeepFiles);
compareManifestList(manifest, "optionalSeedFiles", optionalSeedFiles);
compareManifestList(manifest, "templateGroups", templateGroups);
for (const path of requiredFiles) {
  if (!isSafeFile(path)) {
    failures.push(`missing file: ${path}`);
  }
}
for (const path of requiredDirectories) {
  if (!isSafeDirectory(path)) {
    failures.push(`missing directory: ${path}`);
  }
}
for (const keep of emptyDirectoryKeepFiles) {
  const directory = dirname(keep);
  if (!isSafeDirectory(directory)) {
    continue;
  }
  const realFiles = readdirSync(pathOf(directory)).filter((entry) => entry !== ".gitkeep");
  if (realFiles.length === 0 && !isSafeFile(keep)) {
    failures.push(`empty directory must keep placeholder or real files: ${directory}`);
  }
}
const agentsText = read("AGENTS.md");
const claudeText = read("CLAUDE.md");
const generatedText = [agentsText, claudeText, read("ARCHITECTURE.md")].join("\n");
const evolutionText = [agentsText, claudeText, read(".claude/harness/evolution-log.md")].join("\n");
failures.push(...contentFailures(agentsText, claudeText, generatedText, evolutionText));
for (const doc of requiredAuthoredDocs) {
  if (!isSafeFile(doc)) {
    continue;
  }
  const text = read(doc);
  for (const heading of requiredDocHeadings) {
    if (!text.includes(heading)) {
      failures.push(`doc missing ${heading}: ${doc}`);
    }
  }
}
const agents = walk(".claude/agents").filter((file) => dirname(file) === ".claude/agents" && file.endsWith(".md"));
if (agents.length === 0) {
  failures.push(".claude/agents must contain at least one .md agent");
}
for (const agent of agents) {
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
const skills = walk(".claude/skills").filter((file) => file.endsWith("/SKILL.md"));
if (skills.length === 0) {
  failures.push(".claude/skills must contain at least one SKILL.md");
}
for (const skill of skills) {
  const text = read(skill);
  if (!text.startsWith("---")) {
    failures.push(`skill missing frontmatter: ${skill}`);
  }
  if (!/^description:\s*.+$/m.test(text)) {
    failures.push(`skill missing description: ${skill}`);
  }
}
for (const group of templateGroups) {
  if (!isSafeDirectory(`.claude/harness/templates/${group}`)) {
    failures.push(`missing template group: .claude/harness/templates/${group}`);
  }
}
for (const base of ["AGENTS.md", "CLAUDE.md", "ARCHITECTURE.md", "docs", ".claude/agents", ".claude/skills", ".claude/harness", ".github"]) {
  for (const file of safeFileOrWalk(base)) {
    if (file.startsWith(".claude/harness/templates/") || !/\.(md|txt|json|ya?ml)$/.test(file)) {
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
const hook = ".claude/harness/git-hooks/pre-commit";
if (isSafeFile(hook)) {
  const hookText = read(hook);
  if (firstLine(hook) !== "#!/usr/bin/env sh") {
    failures.push("pre-commit hook must use #!/usr/bin/env sh");
  }
  if (!isExecutablePath(hook)) {
    failures.push(`pre-commit hook must be executable: ${hook}`);
  }
  if (hookText.includes("packaged placeholder is replaced during harness installation")) {
    failures.push("pre-commit hook must be installer-generated selected-mode content");
  }
}
for (const base of [".claude/harness", ".claude/skills"]) {
  for (const file of walk(base)) {
    if (!isExecutablePath(file)) {
      continue;
    }
    if (firstLine(file).startsWith("#!") && !firstLine(file).startsWith("#!/usr/bin/env ")) {
      failures.push(`executable script should use /usr/bin/env shebang: ${file}`);
    }
  }
}
if (failures.length > 0) {
  console.error("Harness validation failed:");
  for (const failure of new Set(failures)) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}
console.log("Harness validation passed");
