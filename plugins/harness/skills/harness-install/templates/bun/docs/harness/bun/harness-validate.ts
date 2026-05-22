#!/usr/bin/env bun
import { lstatSync, readdirSync, readFileSync, readlinkSync, statSync } from "node:fs";
import { dirname, join } from "node:path";

const root = process.cwd();
const STACK = "bun" as const;
const MANIFEST_PATH = "docs/harness/manifest.json";

interface Finding {
  severity: "ERROR" | "WARN" | "INFO";
  category: string;
  message: string;
}

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

function isSafeFile(path: string): readonly [boolean, readonly Finding[]] {
  const findings: Finding[] = [];
  if (isSymlink(path)) {
    if (allowedRootContractTarget(path) === null) {
      findings.push({
        severity: severityOf(manifest, "symlinkSafety"),
        category: "symlinkSafety",
        message: `symlink file is not allowed: ${path}`,
      });
      return [false, findings];
    }
    return [true, findings];
  }
  return [isFile(path), findings];
}

function isSafeDirectory(path: string): readonly [boolean, readonly Finding[]] {
  const findings: Finding[] = [];
  if (isSymlink(path)) {
    findings.push({
      severity: severityOf(manifest, "symlinkSafety"),
      category: "symlinkSafety",
      message: `symlink directory is not allowed: ${path}`,
    });
    return [false, findings];
  }
  return [isDirectory(path), findings];
}

function walk(path: string): readonly [readonly string[], readonly Finding[]] {
  const findings: Finding[] = [];
  if (isSymlink(path)) {
    findings.push({
      severity: severityOf(manifest, "symlinkSafety"),
      category: "symlinkSafety",
      message: `symlink scan root is not allowed: ${path}`,
    });
    return [[], findings];
  }
  if (isFile(path)) {
    return [[path], findings];
  }
  if (!isDirectory(path)) {
    return [[], findings];
  }
  const files: string[] = [];
  for (const entry of readdirSync(pathOf(path))) {
    const child = `${path}/${entry}`;
    const full = pathOf(child);
    if (lstatSync(full).isSymbolicLink()) {
      findings.push({
        severity: severityOf(manifest, "symlinkSafety"),
        category: "symlinkSafety",
        message: `symlink scan entry is not allowed: ${child}`,
      });
      continue;
    }
    if (statSync(full).isDirectory()) {
      const [subFiles, subFindings] = walk(child);
      files.push(...subFiles);
      findings.push(...subFindings);
    }
    if (statSync(full).isFile()) {
      files.push(child);
    }
  }
  return [files, findings];
}

function safeFileOrWalk(path: string): readonly [readonly string[], readonly Finding[]] {
  const findings: Finding[] = [];
  if (isSymlink(path) && allowedRootContractTarget(path) === null) {
    findings.push({
      severity: severityOf(manifest, "symlinkSafety"),
      category: "symlinkSafety",
      message: `symlink path is not allowed: ${path}`,
    });
    return [[], findings];
  }
  const [isSafe] = isSafeFile(path);
  if (isSafe) {
    return [[path], findings];
  }
  return walk(path);
}

function manifestArray(value: unknown): readonly string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function manifestObject(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function severityOf(manifest: Record<string, unknown>, category: string): "ERROR" | "WARN" | "INFO" {
  const sev = manifestObject(manifest[category]).severity;
  if (sev === "ERROR" || sev === "WARN" || sev === "INFO") {
    return sev;
  }
  return "ERROR";
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
function validateStructure(manifest: Record<string, unknown>): readonly Finding[] {
  const findings: Finding[] = [];

  const requiredFilesEntry = manifestObject(manifest.requiredFiles);
  const requiredFiles = manifestArray(requiredFilesEntry.items);
  for (const path of requiredFiles) {
    const [safe, warnings] = isSafeFile(path);
    findings.push(...warnings);
    if (!safe) {
      findings.push({
        severity: severityOf(manifest, "requiredFiles"),
        category: "requiredFiles",
        message: `missing file: ${path}`,
      });
    }
  }

  const requiredDirectoriesEntry = manifestObject(manifest.requiredDirectories);
  const requiredDirectories = manifestArray(requiredDirectoriesEntry.items);
  for (const path of requiredDirectories) {
    const [safe, warnings] = isSafeDirectory(path);
    findings.push(...warnings);
    if (!safe) {
      findings.push({
        severity: severityOf(manifest, "requiredDirectories"),
        category: "requiredDirectories",
        message: `missing directory: ${path}`,
      });
    }
  }

  const emptyDirectoryKeepFilesEntry = manifestObject(manifest.emptyDirectoryKeepFiles);
  const emptyDirectoryKeepFiles = manifestArray(emptyDirectoryKeepFilesEntry.items);
  for (const keep of emptyDirectoryKeepFiles) {
    const directory = dirname(keep);
    const [dirSafe] = isSafeDirectory(directory);
    if (!dirSafe) {
      continue;
    }
    const realFiles = readdirSync(pathOf(directory)).filter((entry) => entry !== ".gitkeep");
    const [keepSafe] = isSafeFile(keep);
    if (realFiles.length === 0 && !keepSafe) {
      findings.push({
        severity: severityOf(manifest, "emptyDirectoryKeepFiles"),
        category: "emptyDirectoryKeepFiles",
        message: `empty directory must keep placeholder or real files: ${directory}`,
      });
    }
  }

  return findings;
}

function validateDocsHeadings(manifest: Record<string, unknown>): readonly Finding[] {
  const findings: Finding[] = [];
  const requiredFilesEntry = manifestObject(manifest.requiredFiles);
  const requiredFiles = manifestArray(requiredFilesEntry.items);
  const requiredDocHeadingsEntry = manifestObject(manifest.requiredDocHeadings);
  const requiredDocHeadings = manifestArray(requiredDocHeadingsEntry.items);

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
        findings.push({
          severity: severityOf(manifest, "requiredDocHeadings"),
          category: "requiredDocHeadings",
          message: `doc missing ${heading}: ${doc}`,
        });
      }
    }
  }

  return findings;
}

function validateContentChecks(manifest: Record<string, unknown>): readonly Finding[] {
  const findings: Finding[] = [];
  const requiredContentChecksEntry = manifestObject(manifest.requiredContentChecks);
  const checks = requiredContentChecksEntry.items;

  if (!Array.isArray(checks)) {
    return findings;
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
      findings.push({
        severity: severityOf(manifest, "requiredContentChecks"),
        category: "requiredContentChecks",
        message: failureMessage,
      });
    }
  }

  return findings;
}

function validateAgents(): readonly Finding[] {
  const findings: Finding[] = [];
  const [agents, agentFindings] = walk(".claude/agents");
  findings.push(...agentFindings);

  const agentFiles = (agents as readonly string[]).filter(
    (file) => dirname(file) === ".claude/agents" && file.endsWith(".md")
  );

  if (agentFiles.length === 0) {
    findings.push({
      severity: severityOf(manifest, "agentFrontmatter"),
      category: "agentFrontmatter",
      message: ".claude/agents must contain at least one .md agent",
    });
  }

  for (const agent of agentFiles) {
    const text = read(agent);
    if (!text.startsWith("---")) {
      findings.push({
        severity: severityOf(manifest, "agentFrontmatter"),
        category: "agentFrontmatter",
        message: `agent missing frontmatter: ${agent}`,
      });
    }
    if (!/^name:\s*[-a-z0-9]+\s*$/m.test(text)) {
      findings.push({
        severity: severityOf(manifest, "agentFrontmatter"),
        category: "agentFrontmatter",
        message: `agent missing name: ${agent}`,
      });
    }
    if (!/^description:\s*.+$/m.test(text)) {
      findings.push({
        severity: severityOf(manifest, "agentFrontmatter"),
        category: "agentFrontmatter",
        message: `agent missing description: ${agent}`,
      });
    }
  }

  return findings;
}

function validateSkills(): readonly Finding[] {
  const findings: Finding[] = [];
  const [skills, skillFindings] = walk(".claude/skills");
  findings.push(...skillFindings);

  const skillFiles = (skills as readonly string[]).filter((file) => file.endsWith("/SKILL.md"));

  if (skillFiles.length === 0) {
    findings.push({
      severity: severityOf(manifest, "skillFrontmatter"),
      category: "skillFrontmatter",
      message: ".claude/skills must contain at least one SKILL.md",
    });
  }

  for (const skill of skillFiles) {
    const text = read(skill);
    if (!text.startsWith("---")) {
      findings.push({
        severity: severityOf(manifest, "skillFrontmatter"),
        category: "skillFrontmatter",
        message: `skill missing frontmatter: ${skill}`,
      });
    }
    if (!/^description:\s*.+$/m.test(text)) {
      findings.push({
        severity: severityOf(manifest, "skillFrontmatter"),
        category: "skillFrontmatter",
        message: `skill missing description: ${skill}`,
      });
    }
  }

  return findings;
}

function validateActiveAssets(manifest: Record<string, unknown>): readonly Finding[] {
  const findings: Finding[] = [];
  const activeAssetsEntry = manifestObject(manifest.activeAssets);
  const activeAssetBases = manifestArray(activeAssetsEntry.bases);
  const excludedActiveAssetSubtrees = manifestArray(activeAssetsEntry.excludedSubtrees);
  const activeAssetExtensions = manifestArray(activeAssetsEntry.extensions);
  const leakPatternsEntry = manifestObject(manifest.leakPatterns);
  const leakPatternsRaw = leakPatternsEntry.items;

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
    findings.push(...warnings);
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
          findings.push({
            severity: severityOf(manifest, "leakPatterns"),
            category: "leakPatterns",
            message: `${label} in active asset: ${file}`,
          });
        }
      }
    }
  }

  return findings;
}

function hookCommand(prePushText: string): string {
  for (const line of prePushText.split(/\r?\n/)) {
    if (line.startsWith("# Harness validation command: ")) {
      return line.replace("# Harness validation command: ", "").trim();
    }
  }
  return "";
}

function validateOneHook(name: string, stage: string): readonly [string, readonly Finding[]] {
  const findings: Finding[] = [];
  const hook = `docs/harness/git-hooks/${name}`;
  let hookText = "";

  const [hookExists, safeWarnings] = isSafeFile(hook);
  findings.push(...safeWarnings);

  if (hookExists) {
    hookText = read(hook);
    if (firstLine(hook) !== "#!/usr/bin/env sh") {
      findings.push({
        severity: severityOf(manifest, "hookFirstLine"),
        category: "hookFirstLine",
        message: `${name} hook must use #!/usr/bin/env sh`,
      });
    }
    if (!isExecutablePath(hook)) {
      findings.push({
        severity: severityOf(manifest, "hookExecutable"),
        category: "hookExecutable",
        message: `${name} hook must be executable: ${hook}`,
      });
    }
    if (!hookText.includes(`Harness generated hook: ${name}`)) {
      findings.push({
        severity: severityOf(manifest, "hookGeneratedMarker"),
        category: "hookGeneratedMarker",
        message: `${name} hook must contain generated marker`,
      });
    }
    if (!hookText.includes(`Harness stage: ${stage}`)) {
      findings.push({
        severity: severityOf(manifest, "hookStages"),
        category: "hookStages",
        message: `${name} hook must contain ${stage} stage marker`,
      });
    }
    if (hookText.includes("packaged placeholder is replaced during harness installation")) {
      findings.push({
        severity: severityOf(manifest, "hookGeneratedMarker"),
        category: "hookGeneratedMarker",
        message: `${name} hook must be installer-generated selected-mode content`,
      });
    }
  }

  return [hookText, findings];
}

function validateHooks(manifest: Record<string, unknown>): readonly Finding[] {
  const findings: Finding[] = [];
  const expectedValidationCommandsEntry = manifestObject(manifest.expectedValidationCommands);
  const hookStagesEntry = manifestObject(manifest.hookStages);
  const stackHookStages = manifestObject(hookStagesEntry[STACK]);

  const preCommitStage = typeof stackHookStages.preCommit === "string" ? stackHookStages.preCommit : "compliance";
  const prePushStage = typeof stackHookStages.prePush === "string" ? stackHookStages.prePush : "full-validation";
  const expectedValidationCommand = typeof expectedValidationCommandsEntry[STACK] === "string"
    ? expectedValidationCommandsEntry[STACK]
    : "";

  const [preCommitText, preCommitFindings] = validateOneHook("pre-commit", preCommitStage);
  findings.push(...preCommitFindings);

  const [prePushText, prePushFindings] = validateOneHook("pre-push", prePushStage);
  findings.push(...prePushFindings);

  if (/(^|\s)(uv|bun|gradle|mvn)(\s|$)|\.\/gradlew|harnessValidate|harness_validate\.py|harness-validate\.ts/.test(preCommitText)) {
    findings.push({
      severity: severityOf(manifest, "expectedValidationCommands"),
      category: "expectedValidationCommands",
      message: "pre-commit hook must not run full stack validation commands",
    });
  }

  const validationCommand = hookCommand(prePushText);
  if (validationCommand.length === 0) {
    findings.push({
      severity: severityOf(manifest, "expectedValidationCommands"),
      category: "expectedValidationCommands",
      message: "pre-push hook must declare Harness validation command",
    });
  } else {
    if (expectedValidationCommand && validationCommand !== expectedValidationCommand) {
      findings.push({
        severity: severityOf(manifest, "expectedValidationCommands"),
        category: "expectedValidationCommands",
        message: `pre-push hook declares unsupported validation command: ${validationCommand}`,
      });
    } else {
      if (!prePushText.split(/\r?\n/).includes(validationCommand)) {
        findings.push({
          severity: severityOf(manifest, "expectedValidationCommands"),
          category: "expectedValidationCommands",
          message: "pre-push hook must run the declared validation command",
        });
      }
      for (const ciFile of [".github/workflows/harness.yml", ".gitlab-ci.yml"]) {
        if (isFile(ciFile)) {
          const [ciSafe, ciWarnings] = isSafeFile(ciFile);
          findings.push(...ciWarnings);
          if (ciSafe && !read(ciFile).includes(validationCommand)) {
            findings.push({
              severity: severityOf(manifest, "ciCommandMatch"),
              category: "ciCommandMatch",
              message: `${ciFile}: CI command mismatch - expected ${validationCommand}`,
            });
          }
        }
      }
    }
  }

  return findings;
}

function validateEnvShebangs(manifest: Record<string, unknown>): readonly Finding[] {
  const findings: Finding[] = [];
  const envShebangBasesEntry = manifestObject(manifest.envShebangBases);
  const envShebangBases = manifestArray(envShebangBasesEntry.items);

  for (const base of envShebangBases) {
    const [files, warnings] = walk(base);
    findings.push(...warnings);
    for (const file of files) {
      if (!isExecutablePath(file)) {
        continue;
      }
      if (firstLine(file).startsWith("#!") && !firstLine(file).startsWith("#!/usr/bin/env ")) {
        findings.push({
          severity: severityOf(manifest, "envShebangBases"),
          category: "envShebangBases",
          message: `executable script should use /usr/bin/env shebang: ${file}`,
        });
      }
    }
  }

  return findings;
}

function validateCompletedPlans(manifest: Record<string, unknown>): readonly Finding[] {
  const findings: Finding[] = [];
  const completedPlanDirectoryEntry = manifestObject(manifest.completedPlanDirectory);
  const completedPlanDirectory = typeof completedPlanDirectoryEntry.value === "string"
    ? completedPlanDirectoryEntry.value
    : "docs/exec-plans/completed";
  const unfinishedTaskPatternEntry = manifestObject(manifest.unfinishedTaskPattern);
  const unfinishedTaskPatternStr = typeof unfinishedTaskPatternEntry.value === "string"
    ? unfinishedTaskPatternEntry.value
    : "";

  if (!unfinishedTaskPatternStr) {
    return findings;
  }

  let unfinishedTaskPattern: RegExp;
  try {
    unfinishedTaskPattern = new RegExp(unfinishedTaskPatternStr);
  } catch {
    return findings;
  }

  const [files, warnings] = walk(completedPlanDirectory);
  findings.push(...warnings);

  for (const file of files) {
    if (!file.endsWith(".md")) {
      continue;
    }
    const text = read(file);
    if (unfinishedTaskPattern.test(text)) {
      findings.push({
        severity: severityOf(manifest, "completedPlanDirectory"),
        category: "completedPlanDirectory",
        message: `completed plan has unchecked tasks: ${file}`,
      });
    }
  }

  return findings;
}

// Main
const manifest = loadManifest();
if (!manifest || typeof manifest !== "object" || Object.keys(manifest).length === 0) {
  const fallbackManifest: Record<string, unknown> = {};
  console.error(`[${severityOf(fallbackManifest, "manifestParity")}] manifest not found or invalid: ` + MANIFEST_PATH);
  process.exit(1);
}

const allFindings: Finding[] = [
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

// Deduplicate findings by (severity, category, message)
const uniqueFindings = new Map<string, Finding>();
for (const finding of allFindings) {
  const key = JSON.stringify([finding.severity, finding.category, finding.message]);
  if (!uniqueFindings.has(key)) {
    uniqueFindings.set(key, finding);
  }
}

// Separate by severity
const errors: Finding[] = [];
const warnings: Finding[] = [];
const infos: Finding[] = [];

for (const finding of uniqueFindings.values()) {
  if (finding.severity === "ERROR") {
    errors.push(finding);
  } else if (finding.severity === "WARN") {
    warnings.push(finding);
  } else {
    infos.push(finding);
  }
}

// Output in order: ERROR → WARN → INFO
for (const error of errors) {
  console.error(`[ERROR] ${error.message}`);
}
for (const warning of warnings) {
  console.error(`[WARN] ${warning.message}`);
}
for (const info of infos) {
  console.error(`[INFO] ${info.message}`);
}

if (errors.length > 0) {
  console.error("Harness validation failed");
  process.exit(1);
}
console.log("Harness validation passed");
