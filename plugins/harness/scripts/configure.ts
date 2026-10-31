import {
  lstat,
  mkdir,
  readFile,
  readdir,
  realpath,
  writeFile
} from "node:fs/promises";
import path from "node:path";
import { parseArgs } from "node:util";

/**
 * Select the fresh root's native build or frontend renderer.
 */
export type BaselineProfile =
  | "gradle"
  | "kotlin"
  | "maven"
  | "go"
  | "rust"
  | "uv"
  | "shell"
  | "node"
  | "frontend"
  | "react";

/**
 * Select a TypeScript base for the target's actual runtime or framework.
 */
export type TypeScriptProfile =
  | "node"
  | "vite-react"
  | "vue"
  | "react-native"
  | "next";

/**
 * Carry the installer's verified repository eligibility decision.
 */
export type BaselineEligibility =
  | "eligible"
  | "archived"
  | "inactive"
  | "configuration-only";

/**
 * Describe files created or retained without exposing target paths.
 */
export interface BaselineResult {
  status: "configured" | "skipped";
  created: string[];
  kept: string[];
}

interface ManifestFragment {
  scripts: Record<string, string>;
  devDependencies: Record<string, string>;
  dependencies?: Record<string, string>;
}

const toolingRoot = path.resolve(import.meta.dirname, "../tooling");
const nativeProfiles = {
  frontend: "pnpm/package.fragment.json",
  go: "go/package.json",
  gradle: "gradle/package.json",
  kotlin: "gradle/package.json",
  maven: "maven/package.json",
  node: "pnpm/package.fragment.json",
  react: "pnpm/package.fragment.json",
  rust: "rust/package.json",
  shell: "shell/package.json",
  uv: "uv/package.json"
};
const typescriptProfiles: Record<
  TypeScriptProfile,
  { dependency: string; config: string }
> = {
  next: {
    config: "@tsconfig/next/tsconfig.json",
    dependency: "@tsconfig/next"
  },
  node: {
    config: "@tsconfig/node-lts/tsconfig.json",
    dependency: "@tsconfig/node-lts"
  },
  "react-native": {
    config: "@tsconfig/react-native/tsconfig.json",
    dependency: "@tsconfig/react-native"
  },
  "vite-react": {
    config: "@tsconfig/vite-react/tsconfig.json",
    dependency: "@tsconfig/vite-react"
  },
  vue: {
    config: "@vue/tsconfig/tsconfig.dom.json",
    dependency: "@vue/tsconfig"
  }
};
const ignoredOutputs: Record<BaselineProfile, string[]> = {
  frontend: ["dist/", "storybook-static/", ".vite/"],
  go: [],
  gradle: [".gradle/", "build/"],
  kotlin: [".gradle/", ".kotlin/", "build/"],
  maven: ["target/"],
  node: ["dist/", "coverage/", "build/api-docs/"],
  react: ["dist/", "storybook-static/", ".vite/"],
  rust: ["target/"],
  shell: [],
  uv: [".venv/", ".ruff_cache/", "__pycache__/", "*.pyc", "build/api-docs/"]
};

const isProfile = (value: string): value is BaselineProfile =>
  Object.hasOwn(nativeProfiles, value);
const isEligibility = (value: string): value is BaselineEligibility =>
  ["eligible", "archived", "inactive", "configuration-only"].includes(value);

const readSource = (relative: string): Promise<string> =>
  readFile(path.join(toolingRoot, relative), "utf-8");

const inspect = async (file: string) => {
  try {
    return await lstat(file);
  } catch (error) {
    if (error instanceof Error && "code" in error && error.code === "ENOENT") {
      return;
    }
    throw error;
  }
};

const addDirectory = async (
  sources: Map<string, string>,
  source: string,
  target: string
): Promise<void> => {
  const entries = await readdir(path.join(toolingRoot, source), {
    withFileTypes: true
  });
  const files = await Promise.all(
    entries
      .filter((entry) => entry.isFile() && !entry.name.endsWith(".test.ts"))
      .map(
        async (entry) =>
          [
            path.join(target, entry.name.replace(/\.fragment$/u, "")),
            await readSource(path.join(source, entry.name))
          ] as const
      )
  );
  for (const [relative, content] of files) {
    sources.set(relative, content);
  }
};

const assertAncestors = async (
  root: string,
  relative: string
): Promise<void> => {
  let current = root;
  const ancestors: string[] = [];
  for (const part of path.dirname(relative).split(path.sep)) {
    current = path.join(current, part);
    ancestors.push(current);
  }
  await Promise.all(
    ancestors.map(async (ancestor) => {
      const entry = await inspect(ancestor);
      if (entry && (!entry.isDirectory() || entry.isSymbolicLink())) {
        throw new Error(`Unsafe destination ancestor for ${relative}.`);
      }
    })
  );
};

const addTypeScriptProfile = async (
  sources: Map<string, string>,
  manifest: ManifestFragment,
  profile: BaselineProfile,
  workspace: string,
  typescriptProfile?: TypeScriptProfile
): Promise<string> => {
  if (!typescriptProfile) {
    return workspace;
  }
  if (!(profile === "node" || profile === "frontend" || profile === "react")) {
    throw new Error(
      "TypeScript profiles require a Node, frontend, or React baseline."
    );
  }
  const base = typescriptProfiles[typescriptProfile];
  manifest.devDependencies[base.dependency] = "catalog:";
  if (typescriptProfile === "node") {
    manifest.devDependencies["@types/node"] = "catalog:";
  }
  const { compilerOptions } = JSON.parse(
    await readSource("pnpm/tsconfig.fragment.json")
  );
  if (typescriptProfile === "react-native") {
    delete compilerOptions.verbatimModuleSyntax;
    compilerOptions.moduleResolution = "bundler";
  }
  if (typescriptProfile === "vue") {
    manifest.devDependencies.typescript = "catalog:vue";
    delete compilerOptions.verbatimModuleSyntax;
  }
  if (typescriptProfile === "vite-react") {
    delete compilerOptions.noFallthroughCasesInSwitch;
    delete compilerOptions.verbatimModuleSyntax;
    compilerOptions.tsBuildInfoFile =
      "./node_modules/.tmp/tsconfig.app.tsbuildinfo";
  }
  sources.set(
    "tsconfig.json",
    `${JSON.stringify(
      {
        compilerOptions,
        extends: base.config,
        ...(typescriptProfile === "next"
          ? {
              exclude: ["node_modules"],
              include: [
                "next-env.d.ts",
                "**/*.ts",
                "**/*.tsx",
                ".next/types/**/*.ts"
              ]
            }
          : {})
      },
      null,
      2
    )}\n`
  );
  if (typescriptProfile === "vue") {
    const vueCatalog = await readSource(
      "pnpm/vue.pnpm-workspace.fragment.yaml"
    );
    return (
      workspace +
      vueCatalog
        .replace("catalog:\n", "catalogs:\n  vue:\n")
        .replace("  typescript:", "    typescript:")
    );
  }
  return workspace;
};

/**
 * Materialize the eligible fresh-root baseline after a complete conflict preview.
 * Preserve identical files, native version catalogs and Rust configuration on repeated runs.
 * Reject differing files or symlink destinations before creating any source.
 * The installer owns eligibility verification, dependency installation and native build integration.
 */
export const configureBaseline = async (
  targetRoot: string,
  profile: BaselineProfile,
  eligibility: BaselineEligibility,
  typescriptProfile?: TypeScriptProfile
): Promise<BaselineResult> => {
  if (eligibility !== "eligible") {
    return { created: [], kept: [], status: "skipped" };
  }
  const root = path.resolve(targetRoot);
  const rootEntry = await lstat(root);
  if (!rootEntry.isDirectory() || rootEntry.isSymbolicLink()) {
    throw new Error("The target must be a regular directory.");
  }
  const sources = new Map<string, string>();
  const manifest: ManifestFragment = JSON.parse(
    await readSource(nativeProfiles[profile])
  );
  let workspace = await readSource("pnpm/pnpm-workspace.fragment.yaml");
  if (profile === "node" || profile === "frontend" || profile === "react") {
    workspace =
      (await readSource("pnpm/allow-builds.fragment.yaml")) + workspace;
  }
  if (profile === "node") {
    const docs: ManifestFragment = JSON.parse(
      await readSource("pnpm/api-docs.package.fragment.json")
    );
    Object.assign(manifest.scripts, docs.scripts);
    Object.assign(manifest.devDependencies, docs.devDependencies);
    const docsCatalog = await readSource(
      "pnpm/api-docs.pnpm-workspace.fragment.yaml"
    );
    workspace += docsCatalog.replace(/^catalog:\n/u, "");
    sources.set("typedoc.json", await readSource("pnpm/typedoc.json"));
  }
  if (profile === "uv") {
    await addDirectory(sources, "uv/docs", "docs");
  }
  if (profile === "frontend" || profile === "react") {
    const extra: ManifestFragment = JSON.parse(
      await readSource(`${profile}/package.fragment.json`)
    );
    Object.assign(manifest.scripts, extra.scripts);
    Object.assign(manifest.devDependencies, extra.devDependencies);
    manifest.dependencies = extra.dependencies;
    const extraCatalog = await readSource(
      `${profile}/pnpm-workspace.fragment.yaml`
    );
    workspace += extraCatalog.replace(/^catalog:\n/u, "");
    await addDirectory(sources, `${profile}/.storybook`, ".storybook");
    sources.set("DESIGN.md", await readSource("frontend/DESIGN.md.fragment"));
  }
  workspace = await addTypeScriptProfile(
    sources,
    manifest,
    profile,
    workspace,
    typescriptProfile
  );
  const runtime = JSON.parse(await readSource("pnpm/runtime.fragment.json"));
  sources.set(
    "package.json",
    `${JSON.stringify(
      Object.fromEntries([
        [
          "name",
          path
            .basename(root)
            .toLowerCase()
            .replaceAll(/[^a-z\d-]/gu, "-")
        ],
        ["private", runtime.private],
        ["type", runtime.type],
        [
          "scripts",
          Object.fromEntries(
            Object.entries(manifest.scripts).toSorted(([left], [right]) =>
              left.localeCompare(right)
            )
          )
        ],
        ...(manifest.dependencies
          ? [["dependencies", manifest.dependencies]]
          : []),
        [
          "devDependencies",
          Object.fromEntries(
            Object.entries(manifest.devDependencies).toSorted(
              ([left], [right]) => left.localeCompare(right)
            )
          )
        ],
        ["engines", runtime.engines]
      ]),
      null,
      2
    )}\n`
  );
  sources.set("pnpm-workspace.yaml", workspace);
  sources.set(
    ".gitignore",
    [
      "node_modules/",
      ".husky/_/",
      ".bin/",
      ...ignoredOutputs[profile],
      ""
    ].join("\n")
  );
  sources.set(
    ".markdownlint-cli2.jsonc",
    await readSource("pnpm/.markdownlint-cli2.jsonc")
  );
  await addDirectory(sources, "pnpm/rules", "rules");
  await addDirectory(
    sources,
    path.join(path.dirname(nativeProfiles[profile]), ".husky"),
    ".husky"
  );
  sources.set(".husky/.gitignore", await readSource("pnpm/.husky/.gitignore"));
  if (profile === "node" || profile === "frontend" || profile === "react") {
    sources.set("oxlint.config.ts", await readSource("pnpm/oxlint.config.ts"));
    sources.set(
      "oxlint.style-no-blank-lines-in-functions.config.ts",
      await readSource(
        "pnpm/oxlint.style-no-blank-lines-in-functions.config.ts"
      )
    );
    sources.set(
      "oxlint.style-no-inline-comments-in-functions.config.ts",
      await readSource(
        "pnpm/oxlint.style-no-inline-comments-in-functions.config.ts"
      )
    );
    sources.set(
      "oxlint.style-sibling-test-spacing.config.ts",
      await readSource("pnpm/oxlint.style-sibling-test-spacing.config.ts")
    );
    sources.set("oxfmt.config.ts", await readSource("pnpm/oxfmt.config.ts"));
    await addDirectory(sources, "pnpm/plugins", "plugins");
  }
  const catalog = "gradle/libs.versions.toml";
  if (profile === "gradle" || profile === "kotlin") {
    sources.set(
      catalog,
      await readSource(
        profile === "kotlin"
          ? "kotlin/gradle/libs.versions.toml"
          : "gradle/libs.versions.toml.fragment"
      )
    );
  }
  if (profile === "rust") {
    const configurations = await Promise.all(
      (
        [
          [".editorconfig"],
          ["rustfmt.toml", ".rustfmt.toml"],
          ["clippy.toml", ".clippy.toml"],
          ["rust-toolchain.toml", "rust-toolchain"]
        ] as const
      ).map(async (names) => {
        const entries = await Promise.all(
          names.map(async (relative) => {
            const target = path.join(root, relative);
            const entry = await inspect(target);
            if (entry && (!entry.isFile() || entry.isSymbolicLink())) {
              throw new Error(`Unsafe destination ${relative}.`);
            }
            return entry
              ? ([relative, await readFile(target, "utf-8")] as const)
              : undefined;
          })
        );
        const existing = entries.filter((entry) => entry !== undefined);
        return existing.length > 0
          ? existing
          : [[names[0], await readSource(`rust/${names[0]}`)] as const];
      })
    );
    for (const [relative, content] of configurations.flat()) {
      sources.set(relative, content);
    }
  }
  const result: BaselineResult = {
    created: [],
    kept: [],
    status: "configured"
  };
  const states = await Promise.all(
    [...sources].map(async ([relative, content]) => {
      await assertAncestors(root, relative);
      const target = path.join(root, relative);
      const entry = await inspect(target);
      if (entry) {
        if (!entry.isFile() || entry.isSymbolicLink()) {
          throw new Error(`Unsafe destination ${relative}.`);
        }
        if (
          relative !== catalog &&
          (await readFile(target, "utf-8")) !== content
        ) {
          throw new Error(
            `Conflict at ${relative}; use a target-owned bounded merge.`
          );
        }
      }
      return { exists: Boolean(entry), relative };
    })
  );
  result.kept = states
    .filter(({ exists }) => exists)
    .map(({ relative }) => relative);
  result.created = states
    .filter(({ exists }) => !exists)
    .map(({ relative }) => relative);
  await Promise.all(
    [...sources]
      .filter(([relative]) => result.created.includes(relative))
      .map(async ([relative, content]) => {
        await mkdir(path.dirname(path.join(root, relative)), {
          recursive: true
        });
        await writeFile(path.join(root, relative), content, { flag: "wx" });
      })
  );
  return result;
};

if (
  process.argv[1] &&
  (await realpath(process.argv[1])) === import.meta.filename
) {
  const { values } = parseArgs({
    options: {
      eligibility: { type: "string" },
      help: { type: "boolean" },
      "non-interactive": { default: true, type: "boolean" },
      profile: { type: "string" },
      target: { type: "string" },
      "typescript-profile": { type: "string" }
    }
  });
  if (values.help) {
    process.stdout.write(
      "Usage: node configure.ts --target <root> --profile <gradle|kotlin|maven|go|rust|uv|shell|node|frontend|react> --eligibility <eligible|archived|inactive|configuration-only> [--typescript-profile <node|vite-react|vue|react-native|next>] --non-interactive\n"
    );
  } else {
    if (
      !values.target ||
      !values.profile ||
      !isProfile(values.profile) ||
      !values.eligibility ||
      !isEligibility(values.eligibility)
    ) {
      throw new Error(
        "Provide --target, a supported --profile, and verified --eligibility."
      );
    }
    if (
      values["typescript-profile"] &&
      !Object.hasOwn(typescriptProfiles, values["typescript-profile"])
    ) {
      throw new Error("Provide a supported --typescript-profile.");
    }
    if (
      values["typescript-profile"] &&
      !["node", "frontend", "react"].includes(values.profile)
    ) {
      throw new Error(
        "TypeScript profiles require a Node, frontend, or React baseline."
      );
    }
    process.stdout.write(
      `${JSON.stringify(await configureBaseline(values.target, values.profile, values.eligibility, values["typescript-profile"] as TypeScriptProfile | undefined))}\n`
    );
  }
}
