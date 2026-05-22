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

interface HarnessCheck {
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
  const sev = readJsonObject(manifest[category]).severity;
  return sev === "ERROR" || sev === "WARN" || sev === "INFO" ? sev : "ERROR";
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

const requireFilesExistCheck: HarnessCheck = {
  category: "requireFilesExist",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireFilesExist);
    return readStringArray(entry.paths).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireFilesExist);
    const paths = readStringArray(entry.paths);
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

const requireDirectoriesExistCheck: HarnessCheck = {
  category: "requireDirectoriesExist",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireDirectoriesExist);
    return readStringArray(entry.paths).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireDirectoriesExist);
    const paths = readStringArray(entry.paths);
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

const requireKeepfileInEmptyDirectoriesCheck: HarnessCheck = {
  category: "requireKeepfileInEmptyDirectories",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireKeepfileInEmptyDirectories);
    return readStringArray(entry.directories).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireKeepfileInEmptyDirectories);
    const directories = readStringArray(entry.directories);
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

const requireTemplateGroupsCheck: HarnessCheck = {
  category: "requireTemplateGroups",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireTemplateGroups);
    return readStringArray(entry.groups).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireTemplateGroups);
    const targetRoot = typeof entry.targetRoot === "string" ? entry.targetRoot : "";
    const groups = readStringArray(entry.groups);
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

const requireDocHeadingsCheck: HarnessCheck = {
  category: "requireDocHeadings",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireDocHeadings);
    return readStringArray(entry.headings).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireDocHeadings);
    const requiredEntry = readJsonObject(manifest[entry.sourceFilesFromCategory ?? "requireFilesExist"]);
    const sourceFilter = readJsonObject(entry.sourceFilter);
    const prefix = typeof sourceFilter.prefix === "string" ? sourceFilter.prefix : "";
    const suffix = typeof sourceFilter.suffix === "string" ? sourceFilter.suffix : "";
    const headings = readStringArray(entry.headings);

    const allSourceFiles = readStringArray(requiredEntry.paths);
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

const requireDocContentCheck: HarnessCheck = {
  category: "requireDocContent",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireDocContent);
    const checks = entry.checks;
    return Array.isArray(checks) && checks.length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireDocContent);
    const checks = entry.checks;
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

const requireAgentFrontmatterCheck: HarnessCheck = {
  category: "requireAgentFrontmatter",
  applies: (manifest) => readJsonObject(manifest.requireAgentFrontmatter).directory !== undefined,
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireAgentFrontmatter);
    const directory = typeof entry.directory === "string" ? entry.directory : "";
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

const requireSkillFrontmatterCheck: HarnessCheck = {
  category: "requireSkillFrontmatter",
  applies: (manifest) => readJsonObject(manifest.requireSkillFrontmatter).rootDirectory !== undefined,
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireSkillFrontmatter);
    const rootDirectory = typeof entry.rootDirectory === "string" ? entry.rootDirectory : "";
    const filename = typeof entry.filename === "string" ? entry.filename : "SKILL.md";

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

const forbidScaffoldLeaksCheck: HarnessCheck = {
  category: "forbidScaffoldLeaks",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.forbidScaffoldLeaks);
    const scope = readJsonObject(entry.scope);
    return readStringArray(scope.bases).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.forbidScaffoldLeaks);
    const scope = readJsonObject(entry.scope);
    const bases = readStringArray(scope.bases);
    const excludedSubtrees = readStringArray(scope.excludedSubtrees);
    const extensions = readStringArray(scope.extensions);
    const patternsRaw = entry.patterns;

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

const requireHookShebangCheck: HarnessCheck = {
  category: "requireHookShebang",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireHookShebang);
    return readStringArray(entry.hooks).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireHookShebang);
    const hooks = readStringArray(entry.hooks);
    const expectedShebang = typeof entry.expectedShebang === "string" ? entry.expectedShebang : "#!/usr/bin/env sh";
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

const requireHookExecutableCheck: HarnessCheck = {
  category: "requireHookExecutable",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireHookExecutable);
    return readStringArray(entry.hooks).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireHookExecutable);
    const hooks = readStringArray(entry.hooks);
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

const requireHookGeneratedMarkerCheck: HarnessCheck = {
  category: "requireHookGeneratedMarker",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireHookGeneratedMarker);
    return readStringArray(entry.hooks).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireHookGeneratedMarker);
    const hooks = readStringArray(entry.hooks);
    const markerTemplate = typeof entry.markerTemplate === "string" ? entry.markerTemplate : "";
    const placeholderForbidden = typeof entry.placeholderForbidden === "string" ? entry.placeholderForbidden : "";

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

const requireHookStageCheck: HarnessCheck = {
  category: "requireHookStage",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireHookStage);
    const stages = readJsonObject(entry.stages);
    return readJsonObject(stages[STACK]).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireHookStage);
    const hooks = readStringArray(entry.hooks);
    const markerTemplate = typeof entry.markerTemplate === "string" ? entry.markerTemplate : "";
    const stagesEntry = readJsonObject(entry.stages);
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

const requireHookCommandCheck: HarnessCheck = {
  category: "requireHookCommand",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireHookCommand);
    return typeof entry.prePushHook === "string";
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireHookCommand);
    const allowedCommands = readJsonObject(entry.allowedCommands);
    const stackCommands = readStringArray(allowedCommands[STACK]);

    const prePushHook = typeof entry.prePushHook === "string" ? entry.prePushHook : "";
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

const requireCiCommandMatchesHookCheck: HarnessCheck = {
  category: "requireCiCommandMatchesHook",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireCiCommandMatchesHook);
    return typeof entry.referenceHook === "string";
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireCiCommandMatchesHook);
    const referenceHook = typeof entry.referenceHook === "string" ? entry.referenceHook : "";
    const ciFiles = readStringArray(entry.ciFiles);

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

const requireEnvShebangUnderCheck: HarnessCheck = {
  category: "requireEnvShebangUnder",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.requireEnvShebangUnder);
    return readStringArray(entry.directories).length > 0;
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.requireEnvShebangUnder);
    const directories = readStringArray(entry.directories);
    const expectedPrefix = typeof entry.expectedPrefix === "string" ? entry.expectedPrefix : "#!/usr/bin/env ";

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

const forbidUncheckedTasksUnderCheck: HarnessCheck = {
  category: "forbidUncheckedTasksUnder",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.forbidUncheckedTasksUnder);
    return typeof entry.directory === "string";
  },
  validate: (root, manifest) => {
    const entry = readJsonObject(manifest.forbidUncheckedTasksUnder);
    const directory = typeof entry.directory === "string" ? entry.directory : "";
    const patternStr = typeof entry.uncheckedTaskPattern === "string" ? entry.uncheckedTaskPattern : "";

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

const forbidUnsafeSymlinksCheck: HarnessCheck = {
  category: "forbidUnsafeSymlinks",
  applies: (manifest) => {
    const entry = readJsonObject(manifest.forbidUnsafeSymlinks);
    return Array.isArray(entry.allowedSymlinkPairs) && entry.allowedSymlinkPairs.length > 0;
  },
  validate: (root, manifest) => {
    return [];
  },
};

const CHECKS: readonly HarnessCheck[] = [
  requireFilesExistCheck,
  requireDirectoriesExistCheck,
  requireKeepfileInEmptyDirectoriesCheck,
  requireTemplateGroupsCheck,
  requireDocHeadingsCheck,
  requireDocContentCheck,
  requireAgentFrontmatterCheck,
  requireSkillFrontmatterCheck,
  forbidScaffoldLeaksCheck,
  requireHookShebangCheck,
  requireHookExecutableCheck,
  requireHookGeneratedMarkerCheck,
  requireHookStageCheck,
  requireHookCommandCheck,
  requireCiCommandMatchesHookCheck,
  requireEnvShebangUnderCheck,
  forbidUncheckedTasksUnderCheck,
  forbidUnsafeSymlinksCheck,
];

function loadManifest(): Manifest {
  if (isSymlink(MANIFEST_PATH)) {
    return {};
  }
  try {
    return JSON.parse(readFileSync(pathOf(MANIFEST_PATH), "utf8"));
  } catch {
    return {};
  }
}

function main(): void {
  const manifest = loadManifest();
  if (!manifest || typeof manifest !== "object" || Object.keys(manifest).length === 0) {
    console.error(`[ERROR] manifest not found or invalid: ${MANIFEST_PATH}`);
    process.exit(1);
  }

  const allFindings = CHECKS.filter((check) => check.applies(manifest)).flatMap((check) => check.validate(root, manifest));

  const uniqueFindings = Array.from(
    new Map(allFindings.map((f) => [`${f.severity}|${f.category}|${f.message}`, f])).values()
  );

  const errors = uniqueFindings.filter((f) => f.severity === "ERROR");
  const warnings = uniqueFindings.filter((f) => f.severity === "WARN");
  const infos = uniqueFindings.filter((f) => f.severity === "INFO");

  errors.forEach((e) => console.error(`[ERROR] ${e.message}`));
  warnings.forEach((w) => console.error(`[WARN] ${w.message}`));
  infos.forEach((i) => console.error(`[INFO] ${i.message}`));

  if (errors.length > 0) {
    console.error("Harness validation failed");
    process.exit(1);
  }
  console.log("Harness validation passed");
}

main();
