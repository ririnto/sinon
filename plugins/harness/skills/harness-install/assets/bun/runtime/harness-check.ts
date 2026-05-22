#!/usr/bin/env bun
import { lstatSync, readdirSync, readFileSync, readlinkSync, statSync } from "node:fs";
import { dirname, join } from "node:path";
import {
  Block,
  ConciseBody,
  Node,
  ScriptTarget,
  SyntaxKind,
  createSourceFile,
  forEachChild,
  getLeadingCommentRanges,
  isBinaryExpression,
  isBlock,
  isCallExpression,
  isCatchClause,
  isClassDeclaration,
  isFunctionDeclaration,
  isIdentifier,
  isIfStatement,
  isImportDeclaration,
  isInterfaceDeclaration,
  isMethodDeclaration,
  isNamespaceImport,
  isNewExpression,
  isPropertyAccessExpression,
  isReturnStatement,
  isStringLiteral,
  isThrowStatement,
  isTypeAliasDeclaration,
  isVariableStatement,
  type FunctionLike,
  type SourceFile,
} from "typescript@6.0.3";

const root = process.cwd();
const STACK = "bun" as const;

interface Finding {
  severity: "ERROR" | "WARN" | "INFO";
  category: string;
  message: string;
}

interface HarnessCheckSpec {
  readonly category: string;
  applies(manifest: Manifest): boolean;
  validate(root: string, manifest: Manifest): readonly Finding[];
}

type Manifest = Record<string, unknown>;

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

function readStringArray(value: unknown): readonly string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
}

function readJsonObject(value: unknown): Record<string, unknown> {
  return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : {};
}

function severityOf(manifest: Manifest, category: string): "ERROR" | "WARN" | "INFO" {
  const section = readJsonObject(manifest[category]);
  const sev = section.severity;
  return sev === "ERROR" || sev === "WARN" || sev === "INFO" ? sev : "ERROR";
}

/**
 * Collect TypeScript/JavaScript source files matching stack configuration.
 *
 * Expands glob entries via Bun.Glob, treats literal paths as directories,
 * filters by configured extensions, and skips node_modules and build directories.
 *
 * @param manifest Harness manifest with sourceRootsPerStack and extensionsPerStack.
 * @param category Stack category (e.g., "typescript").
 * @return Sorted unique list of source file paths relative to root.
 */
function stackSources(manifest: Manifest, category: string): readonly string[] {
  const parameters = readJsonObject(manifest[category]);
  const sourceRootsPerStack = readJsonObject(parameters.sourceRootsPerStack);
  const extensionsPerStack = readJsonObject(parameters.extensionsPerStack);

  const sourceDirs = readStringArray(sourceRootsPerStack[category]);
  const extensions = new Set(readStringArray(extensionsPerStack[category]));

  if (sourceDirs.length === 0 || extensions.size === 0) {
    return [];
  }

  const collected = new Set<string>();

  function* walkDirGen(dirPath: string): Generator<string> {
    const skip = (name: string) => name === "node_modules" || name === "build";

    if (isSymlink(dirPath)) {
      return;
    }
    if (!isDirectory(dirPath)) {
      return;
    }

    try {
      const entries = readdirSync(pathOf(dirPath));
      for (const entry of entries) {
        if (skip(entry)) {
          continue;
        }
        const child = `${dirPath}/${entry}`;
        const full = pathOf(child);
        if (lstatSync(full).isSymbolicLink()) {
          continue;
        }
        const stat = statSync(full);
        if (stat.isDirectory()) {
          yield* walkDirGen(child);
        } else if (stat.isFile()) {
          const ext = child.slice(child.lastIndexOf(".") + 1);
          if (extensions.has(ext)) {
            yield child;
          }
        }
      }
    } catch {
      return;
    }
  }

  for (const sourceDir of sourceDirs) {
    if (sourceDir.includes("*")) {
      try {
        const glob = new Bun.Glob(sourceDir);
        for (const match of glob.scanSync(".")) {
          const normPath = `${sourceDir.split("/")[0]}/${match}`;
          const ext = normPath.slice(normPath.lastIndexOf(".") + 1);
          if (extensions.has(ext)) {
            collected.add(normPath);
          }
        }
      } catch {
        continue;
      }
    } else {
      for (const file of walkDirGen(sourceDir)) {
        collected.add(file);
      }
    }
  }

  return [...collected].sort();
}

/**
 * Traverse TypeScript AST to check if a function node contains nested functions or lambdas.
 *
 * Uses depth-first visit of all child nodes; returns true if any nested function-like
 * node is found (excluding the root function itself).
 *
 * @param node Function-like node to inspect.
 * @return True if node contains nested functions or lambdas.
 */
function hasNestedFunctions(node: FunctionLike): boolean {
  let foundNested = false;
  const visit = (child: Node): void => {
    if (foundNested) {
      return;
    }
    if (child === node) {
      forEachChild(child, visit);
      return;
    }
    switch (child.kind) {
      case SyntaxKind.FunctionDeclaration:
      case SyntaxKind.MethodDeclaration:
      case SyntaxKind.FunctionExpression:
      case SyntaxKind.ArrowFunction:
      case SyntaxKind.Constructor:
        foundNested = true;
        return;
    }
    forEachChild(child, visit);
  };
  forEachChild(node, visit);
  return foundNested;
}

function walkDirectory(path: string): readonly [readonly string[], readonly Finding[]] {
  const findings: Finding[] = [];
  if (isSymlink(path)) {
    findings.push({
      severity: "ERROR",
      category: "forbidUnsafeSymlinks",
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
  const files = readdirSync(pathOf(path))
    .flatMap((entry) => {
      const child = `${path}/${entry}`;
      const full = pathOf(child);
      if (lstatSync(full).isSymbolicLink()) {
        findings.push({
          severity: "ERROR",
          category: "forbidUnsafeSymlinks",
          message: `symlink scan entry is not allowed: ${child}`,
        });
        return [];
      }
      return statSync(full).isDirectory() ? walkDirectory(child)[0] : [child];
    });
  return [files, findings];
}

function collectFilesUnder(path: string): readonly [readonly string[], readonly Finding[]] {
  const findings: Finding[] = [];
  if (isSymlink(path) && allowedRootContractTarget(path) === null) {
    findings.push({
      severity: "ERROR",
      category: "forbidUnsafeSymlinks",
      message: `symlink path is not allowed: ${path}`,
    });
    return [[], findings];
  }
  return isFile(path) ? [[path], findings] : walkDirectory(path);
}

const requireFilesExist: HarnessCheckSpec = {
  category: "requireFilesExist",
  applies: (manifest) => {
    const section = manifest.requireFilesExist;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(entry.paths).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireFilesExist);
    const parameters = readJsonObject(section.parameters);
    const paths = readStringArray(parameters.paths);
    return paths.flatMap((path) => {
      if (isSymlink(path) && allowedRootContractTarget(path) === null) {
        return [
          {
            severity: severityOf(manifest, "requireFilesExist"),
            category: "requireFilesExist",
            message: `symlink file is not allowed: ${path}`,
          },
        ];
      }
      return isFile(path)
        ? []
        : [
            {
              severity: severityOf(manifest, "requireFilesExist"),
              category: "requireFilesExist",
              message: `missing file: ${path}`,
            },
          ];
    });
  },
};

const requireDirectoriesExist: HarnessCheckSpec = {
  category: "requireDirectoriesExist",
  applies: (manifest) => {
    const section = manifest.requireDirectoriesExist;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(entry.paths).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireDirectoriesExist);
    const parameters = readJsonObject(section.parameters);
    const paths = readStringArray(parameters.paths);
    return paths.flatMap((path) => {
      if (isSymlink(path)) {
        return [
          {
            severity: severityOf(manifest, "requireDirectoriesExist"),
            category: "requireDirectoriesExist",
            message: `symlink directory is not allowed: ${path}`,
          },
        ];
      }
      return isDirectory(path)
        ? []
        : [
            {
              severity: severityOf(manifest, "requireDirectoriesExist"),
              category: "requireDirectoriesExist",
              message: `missing directory: ${path}`,
            },
          ];
    });
  },
};

const requireKeepfileInEmptyDirectories: HarnessCheckSpec = {
  category: "requireKeepfileInEmptyDirectories",
  applies: (manifest) => {
    const section = manifest.requireKeepfileInEmptyDirectories;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(entry.directories).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireKeepfileInEmptyDirectories);
    const parameters = readJsonObject(section.parameters);
    const directories = readStringArray(parameters.directories);
    return directories.flatMap((dir) => {
      if (!isDirectory(dir)) {
        return [];
      }
      const realFiles = readdirSync(pathOf(dir)).filter((e) => e !== ".gitkeep");
      const keepPath = `${dir}/.gitkeep`;
      return realFiles.length === 0 && !isFile(keepPath)
        ? [
            {
              severity: severityOf(manifest, "requireKeepfileInEmptyDirectories"),
              category: "requireKeepfileInEmptyDirectories",
              message: `empty directory must keep placeholder or real files: ${dir}`,
            },
          ]
        : [];
    });
  },
};

const requireTemplateGroups: HarnessCheckSpec = {
  category: "requireTemplateGroups",
  applies: (manifest) => {
    const section = manifest.requireTemplateGroups;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(entry.groups).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireTemplateGroups);
    const parameters = readJsonObject(section.parameters);
    const targetRoot = typeof parameters.targetRoot === "string" ? parameters.targetRoot : "";
    const groups = readStringArray(parameters.groups);
    return groups.flatMap((group) => {
      const path = `${targetRoot}/${group}`;
      return isDirectory(path)
        ? []
        : [
            {
              severity: severityOf(manifest, "requireTemplateGroups"),
              category: "requireTemplateGroups",
              message: `missing template group: ${path}`,
            },
          ];
    });
  },
};

const requireDocHeadings: HarnessCheckSpec = {
  category: "requireDocHeadings",
  applies: (manifest) => {
    const section = manifest.requireDocHeadings;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const entry = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(entry.headings).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireDocHeadings);
    const parameters = readJsonObject(section.parameters);
    const sourceCategory = typeof parameters.sourceFilesFromCategory === "string" ? parameters.sourceFilesFromCategory : "requireFilesExist";
    const requiredSection = readJsonObject(manifest[sourceCategory]);
    const requiredParameters = readJsonObject(requiredSection.parameters);
    const sourceFilter = readJsonObject(parameters.sourceFilter);
    const prefix = typeof sourceFilter.prefix === "string" ? sourceFilter.prefix : "";
    const suffix = typeof sourceFilter.suffix === "string" ? sourceFilter.suffix : "";
    const headings = readStringArray(parameters.headings);

    const allSourceFiles = readStringArray(requiredParameters.paths);
    const filteredFiles = allSourceFiles.filter(
      (f) => !prefix || f.startsWith(prefix) && (!suffix || f.endsWith(suffix))
    );

    return filteredFiles.flatMap((file) => {
      if (!isFile(file)) {
        return [];
      }
      const text = read(file);
      return headings.flatMap((heading) =>
        text.includes(heading)
          ? []
          : [
              {
                severity: severityOf(manifest, "requireDocHeadings"),
                category: "requireDocHeadings",
                message: `doc missing ${heading}: ${file}`,
              },
            ]
      );
    });
  },
};

const requireDocContent: HarnessCheckSpec = {
  category: "requireDocContent",
  applies: (manifest) => {
    const section = manifest.requireDocContent;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    const checks = parameters.checks;
    return Array.isArray(checks) && checks.length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireDocContent);
    const parameters = readJsonObject(section.parameters);
    const checks = parameters.checks;
    if (!Array.isArray(checks)) {
      return [];
    }
    return checks.flatMap((check) => {
      if (typeof check !== "object" || check === null) {
        return [];
      }
      const checkObj = check as Record<string, unknown>;
      const files = readStringArray(checkObj.files);
      const containsAll = readStringArray(checkObj.containsAll);
      const failureMessage = typeof checkObj.failureMessage === "string" ? checkObj.failureMessage : "";

      const combinedText = files.map((f) => read(f)).join("\n");
      const hasAllSubstrings = containsAll.every((substring) => combinedText.includes(substring));

      return !hasAllSubstrings && failureMessage
        ? [
            {
              severity: severityOf(manifest, "requireDocContent"),
              category: "requireDocContent",
              message: failureMessage,
            },
          ]
        : [];
    });
  },
};

const requireAgentFrontmatter: HarnessCheckSpec = {
  category: "requireAgentFrontmatter",
  applies: (manifest) => {
    const section = manifest.requireAgentFrontmatter;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return parameters.directory !== undefined;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireAgentFrontmatter);
    const parameters = readJsonObject(section.parameters);
    const directory = typeof parameters.directory === "string" ? parameters.directory : "";
    if (!directory || !isDirectory(directory)) {
      return [];
    }

    const [agents, dirFindings] = walkDirectory(directory);
    const agentFiles = agents.filter((f) => dirname(f) === directory && f.endsWith(".md"));

    if (agentFiles.length === 0) {
      return [
        {
          severity: severityOf(manifest, "requireAgentFrontmatter"),
          category: "requireAgentFrontmatter",
          message: ".claude/agents must contain at least one .md agent",
        },
      ];
    }

    return dirFindings.concat(
      agentFiles.flatMap((agent) => {
        const text = read(agent);
        return [
          !text.startsWith("---")
            ? {
                severity: severityOf(manifest, "requireAgentFrontmatter"),
                category: "requireAgentFrontmatter",
                message: `agent missing frontmatter: ${agent}`,
              }
            : null,
          !/^name:\s*[-a-z0-9]+\s*$/m.test(text)
            ? {
                severity: severityOf(manifest, "requireAgentFrontmatter"),
                category: "requireAgentFrontmatter",
                message: `agent missing name: ${agent}`,
              }
            : null,
          !/^description:\s*.+$/m.test(text)
            ? {
                severity: severityOf(manifest, "requireAgentFrontmatter"),
                category: "requireAgentFrontmatter",
                message: `agent missing description: ${agent}`,
              }
            : null,
        ].filter((f): f is Finding => f !== null);
      })
    );
  },
};

const requireSkillFrontmatter: HarnessCheckSpec = {
  category: "requireSkillFrontmatter",
  applies: (manifest) => {
    const section = manifest.requireSkillFrontmatter;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return parameters.rootDirectory !== undefined;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireSkillFrontmatter);
    const parameters = readJsonObject(section.parameters);
    const rootDirectory = typeof parameters.rootDirectory === "string" ? parameters.rootDirectory : "";
    const filename = typeof parameters.filename === "string" ? parameters.filename : "SKILL.md";

    if (!rootDirectory || !isDirectory(rootDirectory)) {
      return [];
    }

    const [skills, dirFindings] = walkDirectory(rootDirectory);
    const skillFiles = skills.filter((f) => f.endsWith(`/${filename}`));

    if (skillFiles.length === 0) {
      return [
        {
          severity: severityOf(manifest, "requireSkillFrontmatter"),
          category: "requireSkillFrontmatter",
          message: ".claude/skills must contain at least one SKILL.md",
        },
      ];
    }

    return dirFindings.concat(
      skillFiles.flatMap((skill) => {
        const text = read(skill);
        return [
          !text.startsWith("---")
            ? {
                severity: severityOf(manifest, "requireSkillFrontmatter"),
                category: "requireSkillFrontmatter",
                message: `skill missing frontmatter: ${skill}`,
              }
            : null,
          !/^description:\s*.+$/m.test(text)
            ? {
                severity: severityOf(manifest, "requireSkillFrontmatter"),
                category: "requireSkillFrontmatter",
                message: `skill missing description: ${skill}`,
              }
            : null,
        ].filter((f): f is Finding => f !== null);
      })
    );
  },
};

const forbidScaffoldLeaks: HarnessCheckSpec = {
  category: "forbidScaffoldLeaks",
  applies: (manifest) => {
    const section = manifest.forbidScaffoldLeaks;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    const scope = readJsonObject(parameters.scope);
    return readStringArray(scope.bases).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.forbidScaffoldLeaks);
    const parameters = readJsonObject(section.parameters);
    const scope = readJsonObject(parameters.scope);
    const bases = readStringArray(scope.bases);
    const excludedSubtrees = readStringArray(scope.excludedSubtrees);
    const extensions = readStringArray(scope.extensions);
    const patternsRaw = parameters.patterns;

    const patterns: readonly [RegExp, string][] = Array.isArray(patternsRaw)
      ? patternsRaw
          .filter((item): item is Record<string, unknown> => typeof item === "object" && item !== null)
          .flatMap((obj) => {
            const pattern = typeof obj.pattern === "string" ? obj.pattern : "";
            const label = typeof obj.label === "string" ? obj.label : "";
            if (!pattern || !label) {
              return [];
            }
            try {
              return [[new RegExp(pattern), label] as const];
            } catch {
              return [];
            }
          })
      : [];

    return bases.flatMap((base) => {
      const [files, warnings] = collectFilesUnder(base);
      return warnings.concat(
        files.flatMap((file) => {
          const isExcluded = excludedSubtrees.some((subtree) => file === subtree || file.startsWith(`${subtree}/`));
          if (isExcluded) {
            return [];
          }
          const extMatch = /\.([a-z0-9]+)$/.exec(file);
          const ext = extMatch ? extMatch[1] : "";
          if (!extensions.includes(ext)) {
            return [];
          }
          const text = read(file);
          return patterns.flatMap(([pattern, label]) =>
            pattern.test(text)
              ? [
                  {
                    severity: severityOf(manifest, "forbidScaffoldLeaks"),
                    category: "forbidScaffoldLeaks",
                    message: `${label} in active asset: ${file}`,
                  },
                ]
              : []
          );
        })
      );
    });
  },
};

const requireHookShebang: HarnessCheckSpec = {
  category: "requireHookShebang",
  applies: (manifest) => {
    const section = manifest.requireHookShebang;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(parameters.hooks).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireHookShebang);
    const parameters = readJsonObject(section.parameters);
    const hooks = readStringArray(parameters.hooks);
    const expectedShebang = typeof parameters.expectedShebang === "string" ? parameters.expectedShebang : "#!/usr/bin/env sh";
    return hooks.flatMap((hook) => {
      if (!isFile(hook)) {
        return [];
      }
      return firstLine(hook) === expectedShebang
        ? []
        : [
            {
              severity: severityOf(manifest, "requireHookShebang"),
              category: "requireHookShebang",
              message: `${hook} must start with ${expectedShebang}`,
            },
          ];
    });
  },
};

const requireHookExecutable: HarnessCheckSpec = {
  category: "requireHookExecutable",
  applies: (manifest) => {
    const section = manifest.requireHookExecutable;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(parameters.hooks).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireHookExecutable);
    const parameters = readJsonObject(section.parameters);
    const hooks = readStringArray(parameters.hooks);
    return hooks.flatMap((hook) => {
      if (!isFile(hook)) {
        return [];
      }
      return isExecutablePath(hook)
        ? []
        : [
            {
              severity: severityOf(manifest, "requireHookExecutable"),
              category: "requireHookExecutable",
              message: `${hook} must be executable`,
            },
          ];
    });
  },
};

const requireHookGeneratedMarker: HarnessCheckSpec = {
  category: "requireHookGeneratedMarker",
  applies: (manifest) => {
    const section = manifest.requireHookGeneratedMarker;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(parameters.hooks).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireHookGeneratedMarker);
    const parameters = readJsonObject(section.parameters);
    const hooks = readStringArray(parameters.hooks);
    const markerTemplate = typeof parameters.markerTemplate === "string" ? parameters.markerTemplate : "";
    const placeholderForbidden = typeof parameters.placeholderForbidden === "string" ? parameters.placeholderForbidden : "";

    return hooks.flatMap((hook) => {
      if (!isFile(hook)) {
        return [];
      }
      const hookName = hook.split("/").pop() ?? "";
      const marker = markerTemplate.replace("{name}", hookName);
      const text = read(hook);

      return [
        !text.includes(marker)
          ? {
              severity: severityOf(manifest, "requireHookGeneratedMarker"),
              category: "requireHookGeneratedMarker",
              message: `${hook} must contain generated marker '${marker}'`,
            }
          : null,
        placeholderForbidden && text.includes(placeholderForbidden)
          ? {
              severity: severityOf(manifest, "requireHookGeneratedMarker"),
              category: "requireHookGeneratedMarker",
              message: `${hook} still contains packaging placeholder text`,
            }
          : null,
      ].filter((f): f is Finding => f !== null);
    });
  },
};

const requireHookStage: HarnessCheckSpec = {
  category: "requireHookStage",
  applies: (manifest) => {
    const section = manifest.requireHookStage;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    const stages = readJsonObject(parameters.stages);
    return readJsonObject(stages[STACK]).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireHookStage);
    const parameters = readJsonObject(section.parameters);
    const hooks = readStringArray(parameters.hooks);
    const markerTemplate = typeof parameters.markerTemplate === "string" ? parameters.markerTemplate : "";
    const stagesEntry = readJsonObject(parameters.stages);
    const stackStages = readJsonObject(stagesEntry[STACK]);

    return hooks.flatMap((hook) => {
      if (!isFile(hook)) {
        return [];
      }
      const hookName = hook.split("/").pop() ?? "";
      const stageKey = hookName === "pre-commit" ? "pre-commit" : "pre-push";
      const stage = typeof stackStages[stageKey] === "string" ? stackStages[stageKey] : "";
      if (!stage) {
        return [];
      }
      const marker = markerTemplate.replace("{stage}", stage);
      const text = read(hook);

      return text.includes(marker)
        ? []
        : [
            {
              severity: severityOf(manifest, "requireHookStage"),
              category: "requireHookStage",
              message: `${hook} must contain stage marker '${marker}'`,
            },
          ];
    });
  },
};

const requireHookCommand: HarnessCheckSpec = {
  category: "requireHookCommand",
  applies: (manifest) => {
    const section = manifest.requireHookCommand;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return typeof parameters.prePushHook === "string";
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireHookCommand);
    const parameters = readJsonObject(section.parameters);
    const allowedCommands = readJsonObject(parameters.allowedCommands);
    const stackCommands = readStringArray(allowedCommands[STACK]);

    const prePushHook = typeof parameters.prePushHook === "string" ? parameters.prePushHook : "";
    if (!isFile(prePushHook)) {
      return [];
    }

    const prePushText = read(prePushHook);
    const validationCommand = prePushText
      .split(/\r?\n/)
      .find((line) => line.startsWith("# Harness validation command: "))
      ?.replace("# Harness validation command: ", "")
      .trim() ?? "";

    return [
      validationCommand.length === 0
        ? {
            severity: severityOf(manifest, "requireHookCommand"),
            category: "requireHookCommand",
            message: "pre-push hook must declare Harness validation command",
          }
        : null,
      validationCommand && !stackCommands.includes(validationCommand)
        ? {
            severity: severityOf(manifest, "requireHookCommand"),
            category: "requireHookCommand",
            message: `pre-push hook declares unsupported validation command: ${validationCommand}`,
          }
        : null,
      validationCommand && !prePushText.split(/\r?\n/).includes(validationCommand)
        ? {
            severity: severityOf(manifest, "requireHookCommand"),
            category: "requireHookCommand",
            message: "pre-push hook must run the declared validation command",
          }
        : null,
    ].filter((f): f is Finding => f !== null);
  },
};

const requireCiCommandMatchesHook: HarnessCheckSpec = {
  category: "requireCiCommandMatchesHook",
  applies: (manifest) => {
    const section = manifest.requireCiCommandMatchesHook;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return typeof parameters.referenceHook === "string";
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireCiCommandMatchesHook);
    const parameters = readJsonObject(section.parameters);
    const referenceHook = typeof parameters.referenceHook === "string" ? parameters.referenceHook : "";
    const ciFiles = readStringArray(parameters.ciFiles);

    if (!isFile(referenceHook)) {
      return [];
    }

    const refText = read(referenceHook);
    const refCommand = refText
      .split(/\r?\n/)
      .find((line) => line.startsWith("# Harness validation command: "))
      ?.replace("# Harness validation command: ", "")
      .trim() ?? "";

    if (!refCommand) {
      return [];
    }

    return ciFiles.flatMap((ciFile) => {
      if (!isFile(ciFile)) {
        return [];
      }
      return read(ciFile).includes(refCommand)
        ? []
        : [
            {
              severity: severityOf(manifest, "requireCiCommandMatchesHook"),
              category: "requireCiCommandMatchesHook",
              message: `${ciFile}: CI command mismatch — expected ${refCommand}`,
            },
          ];
    });
  },
};

const requireEnvShebangUnder: HarnessCheckSpec = {
  category: "requireEnvShebangUnder",
  applies: (manifest) => {
    const section = manifest.requireEnvShebangUnder;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return readStringArray(parameters.directories).length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireEnvShebangUnder);
    const parameters = readJsonObject(section.parameters);
    const directories = readStringArray(parameters.directories);
    const expectedPrefix = typeof parameters.expectedPrefix === "string" ? parameters.expectedPrefix : "#!/usr/bin/env ";

    return directories.flatMap((dir) => {
      const [files, warnings] = walkDirectory(dir);
      return warnings.concat(
        files.flatMap((file) => {
          if (!isExecutablePath(file)) {
            return [];
          }
          const first = firstLine(file);
          return first.startsWith("#!") && !first.startsWith(expectedPrefix)
            ? [
                {
                  severity: severityOf(manifest, "requireEnvShebangUnder"),
                  category: "requireEnvShebangUnder",
                  message: `executable script should use /usr/bin/env shebang: ${file}`,
                },
              ]
            : [];
        })
      );
    });
  },
};

const forbidUncheckedTasksUnder: HarnessCheckSpec = {
  category: "forbidUncheckedTasksUnder",
  applies: (manifest) => {
    const section = manifest.forbidUncheckedTasksUnder;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return typeof parameters.directory === "string";
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.forbidUncheckedTasksUnder);
    const parameters = readJsonObject(section.parameters);
    const directory = typeof parameters.directory === "string" ? parameters.directory : "";
    const patternStr = typeof parameters.uncheckedTaskPattern === "string" ? parameters.uncheckedTaskPattern : "";

    if (!directory || !isDirectory(directory) || !patternStr) {
      return [];
    }

    let pattern: RegExp;
    try {
      pattern = new RegExp(patternStr);
    } catch {
      return [];
    }

    const [files, warnings] = walkDirectory(directory);
    return warnings.concat(
      files.flatMap((file) => {
        if (!file.endsWith(".md")) {
          return [];
        }
        return pattern.test(read(file))
          ? [
              {
                severity: severityOf(manifest, "forbidUncheckedTasksUnder"),
                category: "forbidUncheckedTasksUnder",
                message: `completed plan has unchecked tasks: ${file}`,
              },
            ]
          : [];
      })
    );
  },
};

const forbidUnsafeSymlinks: HarnessCheckSpec = {
  category: "forbidUnsafeSymlinks",
  applies: (manifest) => {
    const section = manifest.forbidUnsafeSymlinks;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return Array.isArray(parameters.allowedSymlinkPairs) && parameters.allowedSymlinkPairs.length > 0;
  },
  validate: (): readonly Finding[] => {
    return [];
  },
};

const forbidImplicitLambdaIt: HarnessCheckSpec = {
  category: "forbidImplicitLambdaIt",
  applies: (manifest) => {
    const section = manifest.forbidImplicitLambdaIt;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return Array.isArray(parameters.directories) && parameters.directories.length > 0;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.forbidImplicitLambdaIt);
    const parameters = readJsonObject(section.parameters);
    const directories = readStringArray(parameters.directories);
    const suffix = typeof parameters.filenameSuffix === "string" ? parameters.filenameSuffix : ".kt";
    return directories.flatMap((directory) => {
      const [files, warnings] = walkDirectory(directory);
      return warnings.concat(
        files
          .filter((file) => file.endsWith(suffix))
          .flatMap((file) => {
            const text = read(file);
            const lines = text.split(/\r?\n/);
            return lines.flatMap((line, index) => {
              const stripped = line.replace(/"[^"\\]*(?:\\.[^"\\]*)*"/g, "").replace(/\/\/.*$/, "");
              return /\bit\b\s*\./.test(stripped) || /\bit\b\s*\}/.test(stripped) || /->\s*it\b/.test(stripped)
                ? [
                    {
                      severity: severityOf(manifest, "forbidImplicitLambdaIt"),
                      category: "forbidImplicitLambdaIt",
                      message: `Kotlin file ${file} uses implicit \`it\` lambda parameter at line ${index + 1}; use an explicit name`,
                    },
                  ]
                : [];
            });
          })
      );
    });
  },
};

const requireSingleTopLevelKotlinDeclaration: HarnessCheckSpec = {
  category: "requireSingleTopLevelKotlinDeclaration",
  applies: (manifest) => {
    const section = manifest.requireSingleTopLevelKotlinDeclaration;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    const parameters = readJsonObject((section as Record<string, unknown>).parameters);
    return typeof parameters.directories === "object" && parameters.directories !== null;
  },
  validate: (root, manifest) => {
    const section = readJsonObject(manifest.requireSingleTopLevelKotlinDeclaration);
    const parameters = readJsonObject(section.parameters);
    const directories = Array.isArray(parameters.directories) ? parameters.directories : [];
    const directoryStrs = directories.filter((item): item is string => typeof item === "string");

    return directoryStrs.flatMap((directory) => {
      const [files, warnings] = walkDirectory(directory);
      return warnings.concat(
        files.flatMap((file) => {
          if (!file.endsWith(".kt")) {
            return [];
          }
          const text = read(file);
          const declRegex = /^(class|interface|enum class|object|data class|sealed class|val|var|fun|typealias)\s/gm;
          let match: RegExpExecArray | null;
          let count = 0;
          while ((match = declRegex.exec(text)) !== null) {
            count++;
          }
          return count === 1
            ? []
            : [
                {
                  severity: severityOf(manifest, "requireSingleTopLevelKotlinDeclaration"),
                  category: "requireSingleTopLevelKotlinDeclaration",
                  message: `Kotlin file must have exactly 1 top-level declaration: ${file} (found ${count})`,
                },
              ];
        })
      );
    });
  },
};

const forbidGreaterThanComparison: HarnessCheckSpec = {
  category: "forbidGreaterThanComparison",
  applies: (manifest) => {
    const section = manifest.forbidGreaterThanComparison;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }
      let sourceFile: SourceFile;
      try {
        sourceFile = createSourceFile(file, text, ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "forbidGreaterThanComparison"),
            category: "forbidGreaterThanComparison",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }
      const findings: Finding[] = [];
      const visit = (node: Node): void => {
        if (isBinaryExpression(node)) {
          const kind = node.operatorToken.kind;
          if (kind === SyntaxKind.GreaterThanToken || kind === SyntaxKind.GreaterThanEqualsToken) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.operatorToken.getStart(sourceFile));
            const operator = kind === SyntaxKind.GreaterThanToken ? ">" : ">=";
            findings.push({
              severity: severityOf(manifest, "forbidGreaterThanComparison"),
              category: "forbidGreaterThanComparison",
              message: `${file}:${line + 1}: forbidden \`${operator}\`; use \`${operator === ">" ? "<" : "<="}\``,
            });
          }
        }
        forEachChild(node, visit);
      };
      visit(sourceFile);
      return findings;
    });
  },
};

const forbidBlankLineInLeafFunction: HarnessCheckSpec = {
  category: "forbidBlankLineInLeafFunction",
  applies: (manifest) => {
    const section = manifest.forbidBlankLineInLeafFunction;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "forbidBlankLineInLeafFunction"),
            category: "forbidBlankLineInLeafFunction",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const extractBlankLineFindings = (
        funcNode: ts.FunctionLike,
        body: ts.Block | ts.ConciseBody
      ): readonly Finding[] => {
        if (!ts.isBlock(body)) {
          return [];
        }

        const statements = body.statements;
        if (statements.length === 0) {
          return [];
        }

        const funcName =
          (ts.isFunctionDeclaration(funcNode) && funcNode.name?.text) ||
          (ts.isMethodDeclaration(funcNode) && funcNode.name && ts.isIdentifier(funcNode.name) && funcNode.name.text) ||
          "<anonymous>";

        const blankLineFindings: Finding[] = [];

        const checkTrivia = (triviaStart: number, triviaEnd: number): void => {
          const trivia = text.slice(triviaStart, triviaEnd);
          const triviaLines = trivia.split(/\r?\n/);
          const triviaStartLine = sourceFile.getLineAndCharacterOfPosition(triviaStart).line;

          for (let i = 0; i < triviaLines.length; i++) {
            if (triviaLines[i].trim() === "") {
              blankLineFindings.push({
                severity: severityOf(manifest, "forbidBlankLineInLeafFunction"),
                category: "forbidBlankLineInLeafFunction",
                message: `${file}:${triviaStartLine + i + 1}: leaf function \`${funcName}\` contains a blank line; remove or extract the section`,
              });
            }
          }
        };

        if (statements.length > 0) {
          checkTrivia(body.getStart(sourceFile, true), statements[0].getFullStart());
        }

        for (let i = 0; i < statements.length - 1; i++) {
          checkTrivia(statements[i].getEnd(), statements[i + 1].getFullStart());
        }

        if (statements.length > 0) {
          checkTrivia(statements[statements.length - 1].getEnd(), body.getEnd());
        }

        return blankLineFindings;
      };

      const visit = (node: ts.Node): void => {
        switch (node.kind) {
          case ts.SyntaxKind.FunctionDeclaration:
          case ts.SyntaxKind.MethodDeclaration:
          case ts.SyntaxKind.FunctionExpression:
          case ts.SyntaxKind.ArrowFunction:
          case ts.SyntaxKind.Constructor: {
            const funcLike = node as ts.FunctionLike;
            if (funcLike.body && !hasNestedFunctions(funcLike)) {
              findings.push(...extractBlankLineFindings(funcLike, funcLike.body));
            }
            break;
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

/**
 * Check for early return statements in functions.
 *
 * Return statements are only allowed as the final statement of a function body
 * or nested function bodies (which are exempt). Nested function/arrow bodies
 * are checked independently with their own last-statement rule.
 */
const forbidEarlyReturn: HarnessCheckSpec = {
  category: "forbidEarlyReturn",
  applies: (manifest) => {
    const section = manifest.forbidEarlyReturn;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "forbidEarlyReturn"),
            category: "forbidEarlyReturn",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const getFuncName = (funcNode: ts.FunctionLike): string => {
        if (ts.isFunctionDeclaration(funcNode) && funcNode.name) {
          return funcNode.name.text;
        }
        if (ts.isMethodDeclaration(funcNode) && funcNode.name && ts.isIdentifier(funcNode.name)) {
          return funcNode.name.text;
        }
        return "<anonymous>";
      };

      const checkFunc = (funcNode: ts.FunctionLike): void => {
        if (!funcNode.body || !ts.isBlock(funcNode.body)) {
          return;
        }

        const body = funcNode.body;
        const statements = body.statements;
        if (statements.length === 0) {
          return;
        }

        const funcName = getFuncName(funcNode);

        for (let i = 0; i < statements.length - 1; i++) {
          if (ts.isReturnStatement(statements[i])) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(statements[i].getStart(sourceFile));
            findings.push({
              severity: severityOf(manifest, "forbidEarlyReturn"),
              category: "forbidEarlyReturn",
              message: `${file}:${line + 1}: function \`${funcName}\` has an early return; restructure with single exit`,
            });
          }
        }
      };

      const visit = (node: ts.Node): void => {
        switch (node.kind) {
          case ts.SyntaxKind.FunctionDeclaration:
          case ts.SyntaxKind.MethodDeclaration:
          case ts.SyntaxKind.FunctionExpression:
          case ts.SyntaxKind.ArrowFunction:
          case ts.SyntaxKind.Constructor: {
            checkFunc(node as ts.FunctionLike);
            break;
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

/**
 * Check for silent catch blocks.
 *
 * Catch block must not be empty and must contain either a rethrow,
 * a throw statement, or a logger call. Blocks without any of these
 * are considered silent and forbidden.
 */
const forbidSilentCatch: HarnessCheckSpec = {
  category: "forbidSilentCatch",
  applies: (manifest) => {
    const section = manifest.forbidSilentCatch;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "forbidSilentCatch"),
            category: "forbidSilentCatch",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const hasSafeContent = (block: ts.Block): boolean => {
        if (block.statements.length === 0) {
          return false;
        }

        let hasThrowOrRethrow = false;
        let hasCatchVar = false;
        let catchVar = "";

        const visit = (node: ts.Node): void => {
          if (ts.isThrowStatement(node)) {
            hasThrowOrRethrow = true;
          }
          if (ts.isIdentifier(node) && node.text && /^(console|logger|log)/.test(node.text)) {
            hasThrowOrRethrow = true;
          }
          ts.forEachChild(node, visit);
        };

        visit(block);
        return hasThrowOrRethrow;
      };

      const visit = (node: ts.Node): void => {
        if (ts.isCatchClause(node)) {
          if (!hasSafeContent(node.block)) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
            findings.push({
              severity: severityOf(manifest, "forbidSilentCatch"),
              category: "forbidSilentCatch",
              message: `${file}:${line + 1}: silent catch; rethrow, translate to a Finding, or log via structured logger`,
            });
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

/**
 * Check for mutable collection constructors.
 *
 * Detects `new Array(...)`, `new Map(...)`, `new Set(...)` calls
 * and suggests functional alternatives.
 */
const forbidMutableCollection: HarnessCheckSpec = {
  category: "forbidMutableCollection",
  applies: (manifest) => {
    const section = manifest.forbidMutableCollection;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "forbidMutableCollection"),
            category: "forbidMutableCollection",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const visit = (node: ts.Node): void => {
        if (ts.isNewExpression(node)) {
          const expr = node.expression;
          if (ts.isIdentifier(expr)) {
            const name = expr.text;
            if (name === "Array" || name === "Map" || name === "Set") {
              const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
              findings.push({
                severity: severityOf(manifest, "forbidMutableCollection"),
                category: "forbidMutableCollection",
                message: `${file}:${line + 1}: mutable collection construction \`new ${name}\`; use functional alternative`,
              });
            }
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

/**
 * Check for unstructured logging calls.
 *
 * Detects console.log, console.error, console.warn, console.info, console.debug.
 */
const forbidUnstructuredLogging: HarnessCheckSpec = {
  category: "forbidUnstructuredLogging",
  applies: (manifest) => {
    const section = manifest.forbidUnstructuredLogging;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "forbidUnstructuredLogging"),
            category: "forbidUnstructuredLogging",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];
      const logMethods = ["log", "error", "warn", "info", "debug"];

      const visit = (node: ts.Node): void => {
        if (ts.isCallExpression(node)) {
          const expr = node.expression;
          if (ts.isPropertyAccessExpression(expr)) {
            if (ts.isIdentifier(expr.expression) && expr.expression.text === "console") {
              const methodName = expr.name?.text;
              if (methodName && logMethods.includes(methodName)) {
                const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
                findings.push({
                  severity: severityOf(manifest, "forbidUnstructuredLogging"),
                  category: "forbidUnstructuredLogging",
                  message: `${file}:${line + 1}: unstructured logging \`console.${methodName}\`; use structured logger`,
                });
              }
            }
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

/**
 * Check for wildcard imports.
 *
 * Detects `import * as foo from 'module'` patterns.
 */
const forbidWildcardImport: HarnessCheckSpec = {
  category: "forbidWildcardImport",
  applies: (manifest) => {
    const section = manifest.forbidWildcardImport;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "forbidWildcardImport"),
            category: "forbidWildcardImport",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const visit = (node: ts.Node): void => {
        if (ts.isImportDeclaration(node)) {
          if (node.importClause && node.importClause.namedBindings) {
            const bindings = node.importClause.namedBindings;
            if (ts.isNamespaceImport(bindings)) {
              const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
              const module = node.moduleSpecifier && ts.isStringLiteral(node.moduleSpecifier) ? node.moduleSpecifier.text : "module";
              findings.push({
                severity: severityOf(manifest, "forbidWildcardImport"),
                category: "forbidWildcardImport",
                message: `${file}:${line + 1}: wildcard import \`import * as\` forbidden; import explicit symbols`,
              });
            }
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

/**
 * Check for empty catch blocks.
 *
 * Detects catch clauses with no statements.
 */
const forbidEmptyCatchBlock: HarnessCheckSpec = {
  category: "forbidEmptyCatchBlock",
  applies: (manifest) => {
    const section = manifest.forbidEmptyCatchBlock;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "forbidEmptyCatchBlock"),
            category: "forbidEmptyCatchBlock",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const visit = (node: ts.Node): void => {
        if (ts.isCatchClause(node)) {
          if (node.block.statements.length === 0) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
            findings.push({
              severity: severityOf(manifest, "forbidEmptyCatchBlock"),
              category: "forbidEmptyCatchBlock",
              message: `${file}:${line + 1}: empty catch block; handle, rethrow, or convert to a Finding`,
            });
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

/**
 * Check for unbraced if/else statements.
 *
 * All if/else statements must use braced blocks, even for single-statement bodies.
 */
const requireBracesOnIf: HarnessCheckSpec = {
  category: "requireBracesOnIf",
  applies: (manifest) => {
    const section = manifest.requireBracesOnIf;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "requireBracesOnIf"),
            category: "requireBracesOnIf",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const visit = (node: ts.Node): void => {
        if (ts.isIfStatement(node)) {
          if (!ts.isBlock(node.thenStatement)) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
            findings.push({
              severity: severityOf(manifest, "requireBracesOnIf"),
              category: "requireBracesOnIf",
              message: `${file}:${line + 1}: if/else without braces; wrap the body in \`{ ... }\``,
            });
          }
          if (node.elseStatement && !ts.isBlock(node.elseStatement) && !ts.isIfStatement(node.elseStatement)) {
            const { line } = sourceFile.getLineAndCharacterOfPosition(node.elseStatement.getStart(sourceFile));
            findings.push({
              severity: severityOf(manifest, "requireBracesOnIf"),
              category: "requireBracesOnIf",
              message: `${file}:${line + 1}: if/else without braces; wrap the body in \`{ ... }\``,
            });
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

/**
 * Check for doc comments on public declarations.
 *
 * All exported or top-level functions, classes, interfaces, type aliases,
 * and variables must have JSDoc comments.
 */
const requireDocCommentOnPublicDeclaration: HarnessCheckSpec = {
  category: "requireDocCommentOnPublicDeclaration",
  applies: (manifest) => {
    const section = manifest.requireDocCommentOnPublicDeclaration;
    if (typeof section !== "object" || section === null) {
      return false;
    }
    const enabled = (section as { enabled?: unknown }).enabled;
    if (enabled === false) {
      return false;
    }
    return true;
  },
  validate: (root, manifest) => {
    const sources = stackSources(manifest, "typescript");
    return sources.flatMap((file) => {
      const text = read(file);
      if (!text) {
        return [];
      }

      let sourceFile: ts.SourceFile;
      try {
        sourceFile = ts.createSourceFile(file, text, ts.ScriptTarget.Latest, true);
      } catch {
        return [
          {
            severity: severityOf(manifest, "requireDocCommentOnPublicDeclaration"),
            category: "requireDocCommentOnPublicDeclaration",
            message: `failed to parse TypeScript: ${file}`,
          },
        ];
      }

      const findings: Finding[] = [];

      const hasJSDoc = (node: ts.Node): boolean => {
        const fullText = sourceFile.getFullText();
        const leadingComments = ts.getLeadingCommentRanges(fullText, node.getFullStart());
        if (!leadingComments || leadingComments.length === 0) {
          return false;
        }
        const lastComment = leadingComments[leadingComments.length - 1];
        const commentText = fullText.slice(lastComment.pos, lastComment.end);
        return commentText.includes("/**");
      };

      const checkDeclaration = (node: ts.Node, isExported: boolean): void => {
        const name =
          (ts.isFunctionDeclaration(node) && node.name?.text) ||
          (ts.isClassDeclaration(node) && node.name?.text) ||
          (ts.isInterfaceDeclaration(node) && node.name?.text) ||
          (ts.isTypeAliasDeclaration(node) && node.name?.text) ||
          (ts.isVariableStatement(node) && node.declarationList.declarations[0]?.name && ts.isIdentifier(node.declarationList.declarations[0].name) ? node.declarationList.declarations[0].name.text : "");

        if (name && !hasJSDoc(node)) {
          const { line } = sourceFile.getLineAndCharacterOfPosition(node.getStart(sourceFile));
          findings.push({
            severity: severityOf(manifest, "requireDocCommentOnPublicDeclaration"),
            category: "requireDocCommentOnPublicDeclaration",
            message: `${file}:${line + 1}: public declaration \`${name}\` is missing a documentation comment`,
          });
        }
      };

      const visit = (node: ts.Node): void => {
        const isExported = node.modifiers?.some((m) => m.kind === ts.SyntaxKind.ExportKeyword) ?? false;

        if (isExported) {
          switch (node.kind) {
            case ts.SyntaxKind.FunctionDeclaration:
            case ts.SyntaxKind.ClassDeclaration:
            case ts.SyntaxKind.InterfaceDeclaration:
            case ts.SyntaxKind.TypeAliasDeclaration:
            case ts.SyntaxKind.VariableStatement:
              checkDeclaration(node, true);
          }
        }
        ts.forEachChild(node, visit);
      };

      visit(sourceFile);
      return findings;
    });
  },
};

export const HARNESS_CHECKS: readonly HarnessCheckSpec[] = [
  requireFilesExist,
  requireDirectoriesExist,
  requireKeepfileInEmptyDirectories,
  requireTemplateGroups,
  requireDocHeadings,
  requireDocContent,
  requireAgentFrontmatter,
  requireSkillFrontmatter,
  forbidScaffoldLeaks,
  requireHookShebang,
  requireHookExecutable,
  requireHookGeneratedMarker,
  requireHookStage,
  requireHookCommand,
  requireCiCommandMatchesHook,
  requireEnvShebangUnder,
  forbidUncheckedTasksUnder,
  forbidUnsafeSymlinks,
  forbidImplicitLambdaIt,
  requireSingleTopLevelKotlinDeclaration,
  forbidGreaterThanComparison,
  forbidBlankLineInLeafFunction,
  forbidEarlyReturn,
  forbidSilentCatch,
  forbidMutableCollection,
  forbidUnstructuredLogging,
  forbidWildcardImport,
  forbidEmptyCatchBlock,
  requireBracesOnIf,
  requireDocCommentOnPublicDeclaration,
] as const;
