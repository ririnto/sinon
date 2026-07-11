// -*- coding: utf-8 -*-

import path from "node:path";

import {
  rejectTextFragments,
  requireFile,
  requireTexts
} from "./check-support.js";

export const checkRepositoryScripts = (root: string): void => {
  const scripts = path.join(root, "scripts");
  requireTexts([
    {
      fragments: [
        "markdownlint-cli2",
        "checkPluginPackages",
        "ruff",
        "Repository validation passed."
      ],
      path: path.join(scripts, "check.ts")
    },
    {
      fragments: ["markdownlint-cli2", "--fix", "ruff", "check"],
      path: path.join(scripts, "fix.ts")
    }
  ]);
  console.error("[repository scripts] OK");
};

export const checkInstallerSurface = (root: string): void => {
  const scripts = path.join(root, "skills", "harness-install", "scripts");
  requireFile(path.join(scripts, "install-harness.ts"));
  requireFile(path.join(scripts, "asset-manifest.ts"));
  requireFile(path.join(scripts, "generate-asset-manifest.ts"));
  for (const filePath of [
    "atomic-write.ts",
    "cli.ts",
    "commands.ts",
    "content.ts",
    "contracts.ts",
    "decisions.ts",
    "files.ts",
    "hook-activation.ts",
    "installer.ts",
    "managed.ts",
    "operations.ts",
    "paths.ts",
    "planning.ts",
    "preview.ts",
    "record-compatibility.ts",
    "record-content.ts",
    "record-persistence.ts",
    "record-results.ts",
    "record-schema.ts",
    "record-state.ts",
    "record.ts",
    "types.ts"
  ]) {
    requireFile(path.join(scripts, "install-harness", filePath));
  }
  requireFile(
    path.join(
      root,
      "skills",
      "harness-validate",
      "scripts",
      "validate-install-record.ts"
    )
  );
  console.error("[installer surface] OK");
};

export const checkInstallerContract = (root: string): void => {
  const installerDir = path.join(
    root,
    "skills",
    "harness-install",
    "scripts",
    "install-harness"
  );
  requireTexts([
    {
      fragments: [
        '"--activate-hooks"',
        '"--adopt"',
        '"--preview"',
        '"--show"',
        '"--only"'
      ],
      path: path.join(installerDir, "cli.ts")
    },
    {
      fragments: [
        'installRecordPath = ".harness/install-record.json"',
        "expectedPlanDigest",
        "schemaVersion: 2"
      ],
      path: path.join(installerDir, "record-schema.ts")
    },
    {
      fragments: [
        'InstallOutcome = "conflict" | "created" | "kept" | "updated"',
        'AssetOwnership = "harness" | "shared" | "target"'
      ],
      path: path.join(installerDir, "types.ts")
    },
    {
      fragments: [
        "requireCompatibleInstallPlan,",
        "writeInstallRecord",
        "const results = await installFullPlan(config);",
        "await requireCompatibleInstallPlan(config);",
        "await writeInstallRecord(config, results, true);",
        "await writeInstallRecord(config, results, false);"
      ],
      path: path.join(installerDir, "installer.ts")
    },
    {
      fragments: [
        "<!-- harness:managed begin -->",
        "<!-- harness:managed end -->"
      ],
      path: path.join(installerDir, "managed.ts")
    },
    {
      fragments: ["exec:exec@format-markdown spotless:apply"],
      path: path.join(installerDir, "commands.ts")
    }
  ]);
  rejectTextFragments(path.join(installerDir, "cli.ts"), ['"--no-ci"']);
  console.error("[installer contract] OK");
};

export const checkInstallerSecurityContract = (root: string): void => {
  const pluginRoot = root;
  const installerDir = path.join(
    pluginRoot,
    "skills",
    "harness-install",
    "scripts",
    "install-harness"
  );
  requireTexts([
    {
      fragments: [
        "lexical traversal rejection",
        "observed symlink rejection",
        "exclusive same-dir regular temp",
        "immediate-parent identity recheck",
        "destination symlink rejection",
        "trusted, non-hostile target ancestry"
      ],
      path: path.join(pluginRoot, "README.md")
    },
    {
      fragments: [
        "lexical traversal rejection",
        "observed symlink rejection",
        "exclusive same-dir regular temp",
        "immediate-parent identity recheck",
        "destination symlink rejection",
        "concurrent hostile ancestor replacement",
        "APFS normalization",
        "hard-link",
        "containment",
        "trusted, non-hostile target ancestry"
      ],
      path: path.join(pluginRoot, "skills", "harness-install", "SKILL.md")
    },
    {
      fragments: [
        "Best-effort path validation assumes trusted, non-hostile target ancestry"
      ],
      path: path.join(installerDir, "atomic-write.ts")
    }
  ]);
  rejectTextFragments(
    path.join(pluginRoot, "skills", "harness-install", "SKILL.md"),
    [
      "provides full TOCTOU-safe containment",
      "provides descriptor-relative containment"
    ]
  );
  console.error("[installer security contract] OK");
};
