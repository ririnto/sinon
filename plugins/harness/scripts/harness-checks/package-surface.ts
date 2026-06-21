// -*- coding: utf-8 -*-

import {
  existsSync,
  lstatSync,
  readdirSync,
  readFileSync,
  readlinkSync
} from "node:fs";
import path from "node:path";

type JsonValue =
  | boolean
  | null
  | number
  | readonly JsonValue[]
  | string
  | { readonly [key: string]: JsonValue };

const pluginSchema =
  "https://json.schemastore.org/claude-code-plugin-manifest.json";
const marketplaceSchema =
  "https://json.schemastore.org/claude-code-marketplace.json";
const claudeAgentPointer = "# CLAUDE.md\n\n@AGENTS.md\n";
const kebabPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/u;

const rootLinks = {
  ".agents/skills": ".claude/skills",
  ".claude/agents": "plugins/agent-capability-kit/agents",
  ".claude/skills": "plugins/agent-capability-kit/skills",
  ".codex/agents": ".claude/agents"
} as const;

const autoDiscoveredComponentPaths = {
  agents: "./agents/",
  "experimental.monitors": "./monitors/monitors.json",
  "experimental.themes": "./themes/",
  hooks: "./hooks/hooks.json",
  lspServers: "./.lsp.json",
  mcpServers: "./.mcp.json",
  outputStyles: "./output-styles/",
  skills: "./skills/"
} as const;

const manifestMetadataKeys = new Set([
  "$schema",
  "author",
  "description",
  "homepage",
  "keywords",
  "license",
  "name",
  "repository",
  "version"
]);

const manifestStructuredKeys = new Set([
  "channels",
  "defaultEnabled",
  "dependencies",
  "displayName",
  "userConfig"
]);

const manifestComponentKeys = new Set([
  "agents",
  "hooks",
  "lspServers",
  "mcpServers",
  "outputStyles",
  "skills"
]);

const inlineObjectComponentKeys = new Set([
  "hooks",
  "lspServers",
  "mcpServers"
]);
const topLevelExperimentalKeys: Readonly<Record<string, string>> = {
  monitors: "experimental.monitors",
  themes: "experimental.themes"
};

/**
 * Check whether an unknown value is an object record.
 *
 * @param value Value to inspect.
 */
const isRecord = (value: unknown): value is Record<string, JsonValue> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/**
 * Raise a package-surface validation failure.
 *
 * @param message Failure message.
 */
const fail = (message: string): never => {
  throw new Error(message);
};

/**
 * Read one JSON object from disk.
 *
 * @param filePath JSON file path.
 */
const readJsonObject = (filePath: string): Record<string, JsonValue> => {
  const value = JSON.parse(readFileSync(filePath, "utf-8")) as JsonValue;
  if (!isRecord(value)) {
    fail(`${filePath}: top-level JSON value must be an object`);
  }
  return value as Record<string, JsonValue>;
};

/**
 * Parse simple scalar YAML frontmatter.
 *
 * @param filePath Markdown file path.
 */
const parseFrontmatter = (filePath: string): Record<string, string> => {
  const lines = readFileSync(filePath, "utf-8").split(/\r?\n/u);
  if (lines[0]?.trim() !== "---") {
    fail(`${filePath}: missing YAML frontmatter`);
  }
  const end = lines.slice(1).findIndex((line) => line.trim() === "---");
  if (end === -1) {
    fail(`${filePath}: unterminated YAML frontmatter`);
  }
  const values: Record<string, string> = {};
  for (const line of lines.slice(1, end + 1)) {
    if (
      line === "" ||
      line.startsWith(" ") ||
      line.startsWith("\t") ||
      line.startsWith("-")
    ) {
      continue;
    }
    const separator = line.indexOf(":");
    if (separator > 0) {
      values[line.slice(0, separator).trim()] = line
        .slice(separator + 1)
        .trim()
        .replaceAll(/^["']|["']$/gu, "");
    }
  }
  return values;
};

/**
 * Return skill names in one plugin root.
 *
 * @param pluginRoot Plugin root path.
 */
const skillNames = (pluginRoot: string): readonly string[] => {
  const skillsDir = path.join(pluginRoot, "skills");
  if (!existsSync(skillsDir)) {
    return [];
  }
  return readdirSync(skillsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .filter((name) => existsSync(path.join(skillsDir, name, "SKILL.md")))
    .toSorted();
};

/**
 * Check whether a path exists, including broken symlinks.
 *
 * @param filePath Path to inspect.
 */
const pathExists = (filePath: string): boolean =>
  existsSync(filePath) ||
  lstatSync(filePath, { throwIfNoEntry: false }) !== undefined;

/**
 * Resolve a path if it exists.
 *
 * @param filePath Path to resolve.
 */
const resolveExisting = (filePath: string): null | string => {
  try {
    return path.resolve(filePath);
  } catch (error) {
    if (error instanceof Error) {
      return null;
    }
    throw error;
  }
};

/**
 * Check whether a resolved child path stays inside the resolved parent path.
 *
 * @param parent Parent path.
 * @param child Child path.
 */
const isInsidePath = (parent: string, child: string): boolean => {
  const relativePath = path.relative(parent, child);
  return (
    relativePath === "" ||
    (!relativePath.startsWith("..") && !path.isAbsolute(relativePath))
  );
};

/**
 * Return JSON object entries without losing value types.
 *
 * @param value JSON object.
 */
const jsonEntries = (
  value: Record<string, JsonValue>
): readonly (readonly [string, JsonValue])[] => Object.entries(value);

/**
 * Report a package validation error.
 *
 * @param root Repository root.
 * @param filePath Error location.
 * @param message Error message.
 */
const packageError = (
  root: string,
  filePath: string,
  message: string
): string => `${path.relative(root, filePath) || "."}: ${message}`;

/**
 * Validate one root runtime symlink.
 *
 * @param root Repository root.
 * @param link Link path relative to root.
 * @param target Target path relative to root.
 * @param errors Error accumulator.
 */
const validateRootLink = (
  root: string,
  link: string,
  target: string,
  errors: string[]
): void => {
  const linkPath = path.join(root, link);
  const targetPath = path.join(root, target);
  if (!pathExists(linkPath)) {
    errors.push(
      packageError(
        root,
        linkPath,
        `required symlink \`-> ${target}\` is missing`
      )
    );
    return;
  }
  const stat = lstatSync(linkPath);
  if (!stat.isSymbolicLink()) {
    errors.push(
      packageError(root, linkPath, `must be symlink \`-> ${target}\``)
    );
    return;
  }
  const actual = path.resolve(path.dirname(linkPath), readlinkSync(linkPath));
  const expected = resolveExisting(targetPath);
  if (expected === null) {
    errors.push(
      packageError(root, targetPath, "target path cannot be resolved")
    );
    return;
  }
  if (path.resolve(actual) !== expected) {
    errors.push(packageError(root, linkPath, `must resolve to ${target}`));
  }
};

/**
 * Validate repository runtime symlink layout.
 *
 * @param root Repository root.
 * @param errors Error accumulator.
 */
const validateRootLinkLayout = (root: string, errors: string[]): void => {
  for (const [link, target] of Object.entries(rootLinks)) {
    validateRootLink(root, link, target, errors);
  }
  const blockedAgentsPath = path.join(root, ".agents", "agents");
  if (pathExists(blockedAgentsPath)) {
    errors.push(
      packageError(
        root,
        blockedAgentsPath,
        "must not exist; agents are exposed through .codex/agents"
      )
    );
  }
};

/**
 * Validate one manifest string path.
 *
 * @param root Repository root.
 * @param pluginRoot Plugin root.
 * @param manifestPath Manifest path.
 * @param key Manifest key.
 * @param value Manifest path value.
 * @param errors Error accumulator.
 */
const validateManifestStringPath = (
  root: string,
  pluginRoot: string,
  manifestPath: string,
  key: string,
  value: string,
  errors: string[]
): void => {
  if (!value.startsWith("./")) {
    errors.push(
      packageError(root, manifestPath, `${key} path must begin with ./`)
    );
  }
  const declaredPath = path.join(pluginRoot, value.replace(/^\.\//u, ""));
  if (!existsSync(declaredPath)) {
    errors.push(
      packageError(root, manifestPath, `declared path does not exist: ${value}`)
    );
    return;
  }
  if (!isInsidePath(path.resolve(pluginRoot), path.resolve(declaredPath))) {
    errors.push(
      packageError(
        root,
        manifestPath,
        `declared path escapes plugin root: ${value}`
      )
    );
  }
};

/**
 * Validate one manifest component field.
 *
 * @param root Repository root.
 * @param pluginRoot Plugin root.
 * @param manifestPath Manifest path.
 * @param key Manifest key.
 * @param value Manifest value.
 * @param errors Error accumulator.
 */
const validateManifestComponent = (
  root: string,
  pluginRoot: string,
  manifestPath: string,
  key: string,
  value: JsonValue,
  errors: string[]
): void => {
  const defaultPath =
    autoDiscoveredComponentPaths[
      key as keyof typeof autoDiscoveredComponentPaths
    ];
  if (
    defaultPath !== undefined &&
    (value === defaultPath ||
      JSON.stringify(value) === JSON.stringify([defaultPath]))
  ) {
    errors.push(
      packageError(
        root,
        manifestPath,
        `${defaultPath} is auto-discovered; omit ${key} unless combining it with custom paths`
      )
    );
  }
  if (typeof value === "string") {
    validateManifestStringPath(
      root,
      pluginRoot,
      manifestPath,
      key,
      value,
      errors
    );
    return;
  }
  if (Array.isArray(value)) {
    for (const item of value) {
      if (typeof item === "string") {
        validateManifestStringPath(
          root,
          pluginRoot,
          manifestPath,
          key,
          item,
          errors
        );
      } else if (!(key === "experimental.monitors" && isRecord(item))) {
        errors.push(
          packageError(
            root,
            manifestPath,
            `${key} array items must be string paths`
          )
        );
      }
    }
    return;
  }
  if (isRecord(value) && inlineObjectComponentKeys.has(key)) {
    return;
  }
  errors.push(
    packageError(root, manifestPath, `${key} must be a string path or array`)
  );
};

/**
 * Validate one plugin manifest.
 *
 * @param root Repository root.
 * @param pluginRoot Plugin root.
 * @param errors Error accumulator.
 */
const validateManifest = (
  root: string,
  pluginRoot: string,
  errors: string[]
): void => {
  const manifestPath = path.join(pluginRoot, ".claude-plugin", "plugin.json");
  const manifest = readJsonObject(manifestPath);
  if (manifest["$schema"] !== pluginSchema) {
    errors.push(
      packageError(root, manifestPath, `$schema must be ${pluginSchema}`)
    );
  }
  if (manifest["name"] !== path.basename(pluginRoot)) {
    errors.push(
      packageError(
        root,
        manifestPath,
        "name must match plugin directory basename"
      )
    );
  }
  const { author } = manifest;
  if (
    !isRecord(author) ||
    typeof author["name"] !== "string" ||
    author["name"] === ""
  ) {
    errors.push(
      packageError(root, manifestPath, "author must use object form with name")
    );
  }
  if ("interface" in manifest) {
    errors.push(
      packageError(
        root,
        manifestPath,
        "interface must not appear in plugin manifest"
      )
    );
  }
  for (const [key, value] of jsonEntries(manifest)) {
    if (key === "experimental") {
      if (!isRecord(value)) {
        errors.push(
          packageError(root, manifestPath, "experimental must be an object")
        );
        continue;
      }
      for (const experimentalKey of ["monitors", "themes"] as const) {
        const experimentalValue = value[experimentalKey];
        if (experimentalValue !== undefined) {
          validateManifestComponent(
            root,
            pluginRoot,
            manifestPath,
            `experimental.${experimentalKey}`,
            experimentalValue,
            errors
          );
        }
      }
      continue;
    }
    const experimentalAlias = topLevelExperimentalKeys[key];
    if (experimentalAlias !== undefined) {
      validateManifestComponent(
        root,
        pluginRoot,
        manifestPath,
        experimentalAlias,
        value,
        errors
      );
      continue;
    }
    if (manifestMetadataKeys.has(key) || manifestStructuredKeys.has(key)) {
      continue;
    }
    if (!manifestComponentKeys.has(key)) {
      errors.push(
        packageError(root, manifestPath, `unsupported manifest field: ${key}`)
      );
      continue;
    }
    validateManifestComponent(
      root,
      pluginRoot,
      manifestPath,
      key,
      value,
      errors
    );
  }
};

/**
 * Validate packaged skill frontmatter.
 *
 * @param root Repository root.
 * @param pluginRoot Plugin root.
 * @param errors Error accumulator.
 */
const validateSkills = (
  root: string,
  pluginRoot: string,
  errors: string[]
): void => {
  for (const name of skillNames(pluginRoot)) {
    const skillPath = path.join(pluginRoot, "skills", name, "SKILL.md");
    const frontmatter = parseFrontmatter(skillPath);
    if (frontmatter["name"] !== name) {
      errors.push(
        packageError(
          root,
          skillPath,
          "frontmatter name must match skill directory basename"
        )
      );
    }
    if (
      frontmatter["description"] === undefined ||
      frontmatter["description"] === ""
    ) {
      errors.push(
        packageError(root, skillPath, "frontmatter description is required")
      );
    }
  }
};

/**
 * Validate packaged agent frontmatter.
 *
 * @param root Repository root.
 * @param pluginRoot Plugin root.
 * @param errors Error accumulator.
 */
const validateAgents = (
  root: string,
  pluginRoot: string,
  errors: string[]
): void => {
  const agentsDir = path.join(pluginRoot, "agents");
  if (!existsSync(agentsDir)) {
    return;
  }
  for (const entry of readdirSync(agentsDir, { withFileTypes: true })) {
    if (!entry.isFile() || !entry.name.endsWith(".md")) {
      continue;
    }
    const agentPath = path.join(agentsDir, entry.name);
    const stem = path.basename(entry.name, ".md");
    const frontmatter = parseFrontmatter(agentPath);
    if (!kebabPattern.test(stem)) {
      errors.push(
        packageError(root, agentPath, "agent filename stem must use kebab-case")
      );
    }
    if (frontmatter["name"] !== stem) {
      errors.push(
        packageError(
          root,
          agentPath,
          "frontmatter name must match agent filename stem"
        )
      );
    }
  }
};

/**
 * Extract one second-level Markdown section.
 *
 * @param text Markdown text.
 * @param heading Heading to extract.
 */
const extractSection = (text: string, heading: string): string => {
  const start = text.indexOf(`${heading}\n`);
  if (start === -1) {
    return "";
  }
  const rest = text.slice(start + heading.length + 1);
  const nextHeading = rest.search(/^## /mu);
  return nextHeading < 0 ? rest : rest.slice(0, nextHeading);
};

/**
 * Return plugin skill names listed in README inventory.
 *
 * @param readmePath README path.
 */
const listedSkills = (readmePath: string): Set<string> => {
  const text = readFileSync(readmePath, "utf-8");
  const section =
    extractSection(text, "## Included Skills") ||
    extractSection(text, "## Included Skill");
  const names = new Set<string>();
  for (const line of section.split(/\r?\n/u)) {
    const bullet = /^\s*-\s+`(?<name>[a-z0-9]+(?:-[a-z0-9]+)*)`:/u.exec(line);
    if (bullet?.groups?.["name"] !== undefined) {
      names.add(bullet.groups["name"]);
    }
    if (line.trim().startsWith("|")) {
      const [firstCell] = line
        .split("|")
        .slice(1)
        .map((cell) => cell.trim().replaceAll(/^`|`$/gu, ""));
      if (
        firstCell !== undefined &&
        kebabPattern.test(firstCell) &&
        firstCell !== "---"
      ) {
        names.add(firstCell);
      }
    }
  }
  return names;
};

/**
 * Validate plugin README inventory.
 *
 * @param root Repository root.
 * @param pluginRoot Plugin root.
 * @param errors Error accumulator.
 */
const validateReadme = (
  root: string,
  pluginRoot: string,
  errors: string[]
): void => {
  const readmePath = path.join(pluginRoot, "README.md");
  if (!existsSync(readmePath)) {
    errors.push(packageError(root, pluginRoot, "plugin README.md is required"));
    return;
  }
  const actualSkills = new Set(skillNames(pluginRoot));
  const readmeSkills = listedSkills(readmePath);
  if (actualSkills.size > 0 && readmeSkills.size === 0) {
    errors.push(
      packageError(root, readmePath, "Included Skills inventory is required")
    );
    return;
  }
  const missing = [...actualSkills]
    .filter((name) => !readmeSkills.has(name))
    .toSorted();
  const extra = [...readmeSkills]
    .filter((name) => !actualSkills.has(name))
    .toSorted();
  if (actualSkills.size > 0 && (missing.length > 0 || extra.length > 0)) {
    errors.push(
      packageError(
        root,
        readmePath,
        `Included Skills inventory drift; missing: ${missing.join(", ") || "none"}; extra: ${extra.join(", ") || "none"}`
      )
    );
  }
};

/**
 * Validate plugin-local AGENTS and CLAUDE pointer files.
 *
 * @param root Repository root.
 * @param pluginRoot Plugin root.
 * @param errors Error accumulator.
 */
const validatePluginAgentRules = (
  root: string,
  pluginRoot: string,
  errors: string[]
): void => {
  const agentsPath = path.join(pluginRoot, "AGENTS.md");
  const claudePath = path.join(pluginRoot, "CLAUDE.md");
  if (pathExists(agentsPath) && lstatSync(agentsPath).isSymbolicLink()) {
    errors.push(
      packageError(root, agentsPath, "plugin AGENTS.md must be a regular file")
    );
  }
  if (!existsSync(agentsPath)) {
    return;
  }
  if (!existsSync(claudePath)) {
    errors.push(
      packageError(root, claudePath, "plugin CLAUDE.md pointer is required")
    );
    return;
  }
  if (readFileSync(claudePath, "utf-8") !== claudeAgentPointer) {
    errors.push(
      packageError(root, claudePath, "plugin CLAUDE.md must point to AGENTS.md")
    );
  }
};

/**
 * Discover manifested plugin roots.
 *
 * @param root Repository root.
 */
const pluginRoots = (root: string): readonly string[] => {
  const pluginsDir = path.join(root, "plugins");
  return readdirSync(pluginsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => path.join(pluginsDir, entry.name))
    .filter((pluginRoot) =>
      existsSync(path.join(pluginRoot, ".claude-plugin", "plugin.json"))
    )
    .toSorted();
};

/**
 * Validate the root marketplace catalog.
 *
 * @param root Repository root.
 * @param errors Error accumulator.
 */
const validateMarketplace = (root: string, errors: string[]): void => {
  const marketplacePath = path.join(root, ".claude-plugin", "marketplace.json");
  const marketplace = readJsonObject(marketplacePath);
  if (marketplace["$schema"] !== marketplaceSchema) {
    errors.push(
      packageError(
        root,
        marketplacePath,
        `$schema must be ${marketplaceSchema}`
      )
    );
  }
  const entries = marketplace["plugins"];
  if (!Array.isArray(entries)) {
    errors.push(
      packageError(root, marketplacePath, "plugins must be an array")
    );
    return;
  }
  const seenSources = new Set<string>();
  for (const entry of entries) {
    if (!isRecord(entry)) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          "each plugin entry must be an object"
        )
      );
      continue;
    }
    const { name } = entry;
    const { source } = entry;
    if (typeof name !== "string" || typeof source !== "string") {
      errors.push(
        packageError(
          root,
          marketplacePath,
          "plugin entries must include string name and source"
        )
      );
      continue;
    }
    const pluginRoot = path.join(root, source.replace(/^\.\//u, ""));
    if (!source.startsWith("./plugins/") || !existsSync(pluginRoot)) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin source does not exist: ${source}`
        )
      );
      continue;
    }
    if (!isInsidePath(path.join(root, "plugins"), path.resolve(pluginRoot))) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin source escapes plugins directory: ${source}`
        )
      );
      continue;
    }
    const realPluginRoot = path.resolve(pluginRoot);
    if (seenSources.has(realPluginRoot)) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `duplicate plugin source: ${source}`
        )
      );
      continue;
    }
    seenSources.add(realPluginRoot);
    if (name !== path.basename(pluginRoot)) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin name ${name} does not match source basename ${path.basename(pluginRoot)}`
        )
      );
    }
    if (!existsSync(path.join(pluginRoot, ".claude-plugin", "plugin.json"))) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin source lacks manifest: ${source}`
        )
      );
    }
  }
};

/**
 * Validate marketplace package metadata.
 *
 * @param root Harness plugin root.
 */
export const checkPackageSurface = (root: string): void => {
  const repositoryRoot = path.resolve(root, "..", "..");
  const errors: string[] = [];
  validateRootLinkLayout(repositoryRoot, errors);
  validateMarketplace(repositoryRoot, errors);
  for (const pluginRoot of pluginRoots(repositoryRoot)) {
    validateManifest(repositoryRoot, pluginRoot, errors);
    validateSkills(repositoryRoot, pluginRoot, errors);
    validateAgents(repositoryRoot, pluginRoot, errors);
    validateReadme(repositoryRoot, pluginRoot, errors);
    validatePluginAgentRules(repositoryRoot, pluginRoot, errors);
  }
  if (errors.length > 0) {
    throw new Error(`Plugin package validation failed:\n${errors.join("\n")}`);
  }
  console.error("[plugin package surface] OK");
};
