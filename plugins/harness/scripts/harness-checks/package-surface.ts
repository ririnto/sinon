// -*- coding: utf-8 -*-

import { createHash } from "node:crypto";
import {
  existsSync,
  lstatSync,
  readdirSync,
  readFileSync,
  readlinkSync,
  realpathSync
} from "node:fs";
import path from "node:path";

type JsonValue =
  | boolean
  | null
  | number
  | readonly JsonValue[]
  | string
  | { readonly [key: string]: JsonValue };

type TomlValue =
  | boolean
  | null
  | number
  | readonly TomlValue[]
  | string
  | { readonly [key: string]: TomlValue };

const pluginSchema =
  "https://json.schemastore.org/claude-code-plugin-manifest.json";
const marketplaceSchema =
  "https://json.schemastore.org/claude-code-marketplace.json";
const claudeAgentPointer = "# CLAUDE.md\n\n@AGENTS.md\n";
const apacheLicenseSha256 =
  "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30";
const mitLicenseSha256 =
  "3990b88c59157cdbc68c004fc583af6eb6204fe23f95f7620e16e74e8ac65c12";
const kebabPattern = /^[a-z0-9]+(?:-[a-z0-9]+)*$/u;

const rootLinks = {
  ".agents/skills": ".claude/skills",
  ".claude/agents": "plugins/agent-capability-kit/agents",
  ".claude/skills": "plugins/agent-capability-kit/skills",
  "scripts/no-box-drawing.ts":
    "plugins/harness/skills/harness-install/assets/common/scripts/no-box-drawing.ts"
} as const;

const autoDiscoveredComponentPaths = {
  agents: "./agents/",
  commands: "./commands/",
  hooks: "./hooks/hooks.json",
  lspServers: "./.lsp.json",
  mcpServers: "./.mcp.json",
  monitors: "./monitors/monitors.json",
  outputStyles: "./output-styles/",
  skills: "./skills/",
  themes: "./themes/"
} as const;

const manifestFields = new Set([
  "$schema",
  "agents",
  "author",
  "channels",
  "commands",
  "dependencies",
  "description",
  "homepage",
  "hooks",
  "keywords",
  "license",
  "lspServers",
  "mcpServers",
  "monitors",
  "name",
  "outputStyles",
  "repository",
  "settings",
  "skills",
  "themes",
  "userConfig",
  "version"
]);

const pathOnlyComponentFields = new Set([
  "agents",
  "outputStyles",
  "skills",
  "themes"
]);

const mixedComponentFields = new Set(["hooks", "lspServers", "mcpServers"]);

const supportedAgentFields = new Set([
  "background",
  "color",
  "description",
  "disallowedTools",
  "effort",
  "isolation",
  "maxTurns",
  "memory",
  "model",
  "name",
  "skills",
  "tools"
]);

/** Check whether an unknown value is an object record. */
const isRecord = (value: unknown): value is Record<string, JsonValue> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

/** Raise a package-surface validation failure. */
const fail = (message: string): never => {
  throw new Error(message);
};

/** Read one JSON object from disk. */
const readJsonObject = (filePath: string): Record<string, JsonValue> => {
  const value = JSON.parse(readFileSync(filePath, "utf-8")) as JsonValue;
  if (isRecord(value)) {
    return value;
  }
  return fail(`${filePath}: top-level JSON value must be an object`);
};

/** Read one TOML object from disk. */
const readTomlObject = (filePath: string): Record<string, TomlValue> => {
  const value = Bun.TOML.parse(readFileSync(filePath, "utf-8")) as unknown;
  if (typeof value === "object" && value !== null && !Array.isArray(value)) {
    return value as Record<string, TomlValue>;
  }
  return fail(`${filePath}: top-level TOML value must be an object`);
};

/** Parse complete YAML frontmatter with Bun's YAML parser. */
const parseFrontmatter = (filePath: string): Record<string, JsonValue> => {
  const lines = readFileSync(filePath, "utf-8").split(/\r?\n/u);
  if (lines[0] !== "---") {
    fail(`${filePath}: missing YAML frontmatter`);
  }
  const end = lines.indexOf("---", 1);
  if (end === -1) {
    fail(`${filePath}: unterminated YAML frontmatter`);
  }
  let value: unknown;
  try {
    value = Bun.YAML.parse(lines.slice(1, end).join("\n"));
  } catch (error) {
    fail(
      `${filePath}: invalid YAML frontmatter: ${
        error instanceof Error ? error.message : String(error)
      }`
    );
  }
  if (isRecord(value)) {
    return value;
  }
  return fail(`${filePath}: YAML frontmatter must be an object`);
};

/** Return a stable SHA-256 digest for one string. */
const sha256 = (content: string): string =>
  createHash("sha256").update(content).digest("hex");

/** Check whether a path exists, including a broken symlink. */
const pathExists = (filePath: string): boolean =>
  existsSync(filePath) ||
  lstatSync(filePath, { throwIfNoEntry: false }) !== undefined;

/** Check whether a child path stays inside a parent path. */
const isInsidePath = (parent: string, child: string): boolean => {
  const relativePath = path.relative(parent, child);
  return (
    relativePath === "" ||
    (!relativePath.startsWith("..") && !path.isAbsolute(relativePath))
  );
};

/** Return JSON object entries without losing value types. */
const jsonEntries = (
  value: Record<string, JsonValue>
): readonly (readonly [string, JsonValue])[] => Object.entries(value);

/** Report a package validation error relative to the repository root. */
const packageError = (
  root: string,
  filePath: string,
  message: string
): string => `${path.relative(root, filePath) || "."}: ${message}`;

/** Validate one required root symlink and its dereferenced target. */
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
  if (!lstatSync(linkPath).isSymbolicLink()) {
    errors.push(
      packageError(root, linkPath, `must be symlink \`-> ${target}\``)
    );
    return;
  }
  const linkTarget = readlinkSync(linkPath);
  const declaredTarget = path.resolve(path.dirname(linkPath), linkTarget);
  const expectedLinkTarget = path
    .relative(path.dirname(linkPath), targetPath)
    .split(path.sep)
    .join("/");
  if (linkTarget !== expectedLinkTarget) {
    errors.push(
      packageError(
        root,
        linkPath,
        `must use symlink target ${expectedLinkTarget}`
      )
    );
    return;
  }
  if (declaredTarget !== path.resolve(targetPath)) {
    errors.push(packageError(root, linkPath, `must point to ${target}`));
    return;
  }
  try {
    if (realpathSync(linkPath) !== realpathSync(targetPath)) {
      errors.push(packageError(root, linkPath, `must resolve to ${target}`));
    }
  } catch (error) {
    errors.push(
      packageError(
        root,
        linkPath,
        `cannot resolve symlink target: ${
          error instanceof Error ? error.message : String(error)
        }`
      )
    );
  }
};

/** Validate repository pointers, symlinks, and Codex agent layout. */
const validateRootLayout = (root: string, errors: string[]): void => {
  const claudePath = path.join(root, "CLAUDE.md");
  if (!existsSync(claudePath)) {
    errors.push(packageError(root, claudePath, "root pointer is missing"));
  } else if (readFileSync(claudePath, "utf-8") !== claudeAgentPointer) {
    errors.push(
      packageError(root, claudePath, "must point exactly to root AGENTS.md")
    );
  }
  for (const [link, target] of Object.entries(rootLinks)) {
    validateRootLink(root, link, target, errors);
  }
  const codexAgentsPath = path.join(root, ".codex", "agents");
  if (!pathExists(codexAgentsPath)) {
    errors.push(
      packageError(
        root,
        codexAgentsPath,
        "required Codex agent directory is missing"
      )
    );
    return;
  }
  if (!lstatSync(codexAgentsPath).isDirectory()) {
    errors.push(
      packageError(
        root,
        codexAgentsPath,
        "must be a regular directory of TOML agents"
      )
    );
    return;
  }
  const codexEntries = readdirSync(codexAgentsPath, { withFileTypes: true });
  const codexNames = new Set<string>();
  for (const entry of codexEntries) {
    const filePath = path.join(codexAgentsPath, entry.name);
    if (!entry.isFile() || !entry.name.endsWith(".toml")) {
      errors.push(
        packageError(
          root,
          filePath,
          "Codex agent directory may contain only TOML files"
        )
      );
      continue;
    }
    const name = entry.name.slice(0, -".toml".length);
    codexNames.add(name);
    const toml = readTomlObject(filePath);
    if (toml["name"] !== name) {
      errors.push(
        packageError(root, filePath, `name must match basename ${name}`)
      );
    }
    for (const field of ["description", "developer_instructions"] as const) {
      if (typeof toml[field] !== "string" || toml[field] === "") {
        errors.push(
          packageError(root, filePath, `${field} must be a non-empty string`)
        );
      }
    }
    if (
      toml["sandbox_mode"] !== undefined &&
      typeof toml["sandbox_mode"] !== "string"
    ) {
      errors.push(
        packageError(root, filePath, "sandbox_mode must be a string")
      );
    }
  }
  const sharedAgentsPath = path.join(
    root,
    "plugins",
    "agent-capability-kit",
    "agents"
  );
  const sharedNames = new Set(
    readdirSync(sharedAgentsPath, { withFileTypes: true })
      .filter((entry) => entry.isFile() && entry.name.endsWith(".md"))
      .map((entry) => entry.name.slice(0, -".md".length))
  );
  for (const name of sharedNames) {
    if (!codexNames.has(name)) {
      errors.push(
        packageError(
          root,
          codexAgentsPath,
          `missing Codex TOML for shared agent ${name}`
        )
      );
    }
  }
  for (const name of codexNames) {
    if (!sharedNames.has(name)) {
      errors.push(
        packageError(
          root,
          codexAgentsPath,
          `unexpected Codex TOML without shared agent ${name}`
        )
      );
    }
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

/** Validate one manifest path and reject lexical or symlink escapes. */
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
    return;
  }
  const pluginRealPath = realpathSync(pluginRoot);
  const declaredPath = path.resolve(pluginRoot, value);
  if (!isInsidePath(pluginRealPath, declaredPath)) {
    errors.push(
      packageError(
        root,
        manifestPath,
        `declared path escapes plugin root: ${value}`
      )
    );
    return;
  }
  if (!pathExists(declaredPath)) {
    errors.push(
      packageError(root, manifestPath, `declared path does not exist: ${value}`)
    );
    return;
  }
  try {
    if (!isInsidePath(pluginRealPath, realpathSync(declaredPath))) {
      errors.push(
        packageError(
          root,
          manifestPath,
          `declared path resolves outside plugin root: ${value}`
        )
      );
    }
  } catch (error) {
    errors.push(
      packageError(
        root,
        manifestPath,
        `declared path cannot be resolved: ${value}: ${
          error instanceof Error ? error.message : String(error)
        }`
      )
    );
  }
};

/** Report a manifest field that only restates its auto-discovered path. */
const validateDefaultPathRestatement = (
  root: string,
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
        `${defaultPath} is auto-discovered; omit ${key} when it is the only value`
      )
    );
  }
};

/** Validate a string path or array of string paths. */
const validatePathComponent = (
  root: string,
  pluginRoot: string,
  manifestPath: string,
  key: string,
  value: JsonValue,
  errors: string[]
): void => {
  validateDefaultPathRestatement(root, manifestPath, key, value, errors);
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
  if (
    Array.isArray(value) &&
    value.every((item): item is string => typeof item === "string")
  ) {
    for (const item of value) {
      validateManifestStringPath(
        root,
        pluginRoot,
        manifestPath,
        key,
        item,
        errors
      );
    }
    return;
  }
  errors.push(
    packageError(
      root,
      manifestPath,
      `${key} must be a string path or path array`
    )
  );
};

/** Validate a component that accepts paths and inline object definitions. */
const validateMixedComponent = (
  root: string,
  pluginRoot: string,
  manifestPath: string,
  key: string,
  value: JsonValue,
  errors: string[]
): void => {
  validateDefaultPathRestatement(root, manifestPath, key, value, errors);
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
  if (isRecord(value)) {
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
      } else if (!isRecord(item)) {
        errors.push(
          packageError(
            root,
            manifestPath,
            `${key} array items must be paths or inline objects`
          )
        );
      }
    }
    return;
  }
  errors.push(
    packageError(root, manifestPath, `${key} must be a path, object, or array`)
  );
};

/** Validate current command path and inline-object forms. */
const validateCommands = (
  root: string,
  pluginRoot: string,
  manifestPath: string,
  value: JsonValue,
  errors: string[]
): void => {
  if (typeof value === "string" || Array.isArray(value)) {
    validatePathComponent(
      root,
      pluginRoot,
      manifestPath,
      "commands",
      value,
      errors
    );
    return;
  }
  if (!isRecord(value)) {
    errors.push(
      packageError(
        root,
        manifestPath,
        "commands must be paths or an object map"
      )
    );
    return;
  }
  const allowedFields = new Set([
    "allowedTools",
    "argumentHint",
    "content",
    "description",
    "model",
    "source"
  ]);
  for (const [name, command] of jsonEntries(value)) {
    if (!isRecord(command)) {
      errors.push(
        packageError(root, manifestPath, `commands.${name} must be an object`)
      );
      continue;
    }
    for (const [field, fieldValue] of jsonEntries(command)) {
      if (!allowedFields.has(field)) {
        errors.push(
          packageError(
            root,
            manifestPath,
            `commands.${name} has unsupported field ${field}`
          )
        );
      } else if (field === "source" && typeof fieldValue === "string") {
        validateManifestStringPath(
          root,
          pluginRoot,
          manifestPath,
          `commands.${name}.source`,
          fieldValue,
          errors
        );
      } else if (
        field === "allowedTools" &&
        (!Array.isArray(fieldValue) ||
          !fieldValue.every((item) => typeof item === "string"))
      ) {
        errors.push(
          packageError(
            root,
            manifestPath,
            `commands.${name}.allowedTools must be a string array`
          )
        );
      } else if (field !== "allowedTools" && typeof fieldValue !== "string") {
        errors.push(
          packageError(
            root,
            manifestPath,
            `commands.${name}.${field} must be a string`
          )
        );
      }
    }
  }
};

/** Validate the current path-or-inline monitor forms. */
const validateMonitors = (
  root: string,
  pluginRoot: string,
  manifestPath: string,
  value: JsonValue,
  errors: string[]
): void => {
  validateDefaultPathRestatement(root, manifestPath, "monitors", value, errors);
  if (typeof value === "string") {
    validateManifestStringPath(
      root,
      pluginRoot,
      manifestPath,
      "monitors",
      value,
      errors
    );
    return;
  }
  if (!Array.isArray(value)) {
    errors.push(
      packageError(
        root,
        manifestPath,
        "monitors must be a JSON path or inline array"
      )
    );
    return;
  }
  const allowedFields = new Set(["command", "description", "name", "when"]);
  for (const [index, monitor] of value.entries()) {
    if (!isRecord(monitor)) {
      errors.push(
        packageError(root, manifestPath, `monitors[${index}] must be an object`)
      );
      continue;
    }
    for (const field of ["name", "command", "description"] as const) {
      if (typeof monitor[field] !== "string" || monitor[field] === "") {
        errors.push(
          packageError(
            root,
            manifestPath,
            `monitors[${index}].${field} must be a non-empty string`
          )
        );
      }
    }
    if (
      monitor["when"] !== undefined &&
      (typeof monitor["when"] !== "string" ||
        (monitor["when"] !== "always" &&
          !monitor["when"].startsWith("on-skill-invoke:")))
    ) {
      errors.push(
        packageError(root, manifestPath, `monitors[${index}].when is invalid`)
      );
    }
    for (const field of Object.keys(monitor)) {
      if (!allowedFields.has(field)) {
        errors.push(
          packageError(
            root,
            manifestPath,
            `monitors[${index}] has unsupported field ${field}`
          )
        );
      }
    }
  }
};

/** Validate one current manifest field. */
const validateManifestField = (
  root: string,
  pluginRoot: string,
  manifestPath: string,
  key: string,
  value: JsonValue,
  errors: string[]
): void => {
  if (!manifestFields.has(key)) {
    errors.push(
      packageError(root, manifestPath, `unsupported manifest field: ${key}`)
    );
  } else if (pathOnlyComponentFields.has(key)) {
    validatePathComponent(root, pluginRoot, manifestPath, key, value, errors);
  } else if (mixedComponentFields.has(key)) {
    validateMixedComponent(root, pluginRoot, manifestPath, key, value, errors);
  } else if (key === "commands") {
    validateCommands(root, pluginRoot, manifestPath, value, errors);
  } else if (key === "monitors") {
    validateMonitors(root, pluginRoot, manifestPath, value, errors);
  } else if ((key === "settings" || key === "userConfig") && !isRecord(value)) {
    errors.push(packageError(root, manifestPath, `${key} must be an object`));
  } else if (
    (key === "channels" || key === "dependencies") &&
    !Array.isArray(value)
  ) {
    errors.push(packageError(root, manifestPath, `${key} must be an array`));
  }
};

/** Validate one plugin manifest against the current Claude schema surface. */
const validateManifest = (
  root: string,
  pluginRoot: string,
  errors: string[]
): Record<string, JsonValue> => {
  const manifestPath = path.join(pluginRoot, ".claude-plugin", "plugin.json");
  const manifest = readJsonObject(manifestPath);
  const { author, keywords } = manifest;
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
  for (const field of ["description", "license"] as const) {
    if (typeof manifest[field] !== "string" || manifest[field] === "") {
      errors.push(
        packageError(root, manifestPath, `${field} must be a non-empty string`)
      );
    }
  }
  if (
    !isRecord(author) ||
    typeof author["name"] !== "string" ||
    author["name"] === ""
  ) {
    errors.push(
      packageError(root, manifestPath, "author must use object form with name")
    );
  }
  for (const field of ["homepage", "repository", "version"] as const) {
    if (manifest[field] !== undefined && typeof manifest[field] !== "string") {
      errors.push(
        packageError(root, manifestPath, `${field} must be a string`)
      );
    }
  }
  if (
    keywords !== undefined &&
    (!Array.isArray(keywords) ||
      !keywords.every((item) => typeof item === "string"))
  ) {
    errors.push(
      packageError(root, manifestPath, "keywords must be a string array")
    );
  }
  for (const [key, value] of jsonEntries(manifest)) {
    validateManifestField(root, pluginRoot, manifestPath, key, value, errors);
  }
  return manifest;
};

/** Return every direct skill directory, including malformed ones. */
const skillDirectories = (pluginRoot: string): readonly string[] => {
  const skillsDir = path.join(pluginRoot, "skills");
  if (!existsSync(skillsDir)) {
    return [];
  }
  return readdirSync(skillsDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .toSorted();
};

/** Validate every packaged skill directory and current frontmatter fields. */
const validateSkills = (
  root: string,
  pluginRoot: string,
  errors: string[]
): void => {
  for (const name of skillDirectories(pluginRoot)) {
    const skillPath = path.join(pluginRoot, "skills", name, "SKILL.md");
    if (!existsSync(skillPath)) {
      errors.push(
        packageError(
          root,
          skillPath,
          "every skill directory must contain SKILL.md"
        )
      );
      continue;
    }
    const frontmatter = parseFrontmatter(skillPath);
    if (!kebabPattern.test(name) || frontmatter["name"] !== name) {
      errors.push(
        packageError(
          root,
          skillPath,
          "frontmatter name must match the kebab-case skill directory basename"
        )
      );
    }
    const { description } = frontmatter;
    if (
      typeof description !== "string" ||
      description.length < 1 ||
      description.length > 1024
    ) {
      errors.push(
        packageError(
          root,
          skillPath,
          "description must contain 1-1024 characters"
        )
      );
    }
    for (const field of Object.keys(frontmatter)) {
      if (field !== "name" && field !== "description") {
        errors.push(
          packageError(
            root,
            skillPath,
            `unsupported skill frontmatter field: ${field}`
          )
        );
      }
    }
  }
};

/** Check whether a value is a string or string array. */
const isStringList = (value: JsonValue): boolean =>
  typeof value === "string" ||
  (Array.isArray(value) && value.every((item) => typeof item === "string"));

/** Validate one supported agent frontmatter field. */
const validateAgentField = (
  root: string,
  agentPath: string,
  field: string,
  value: JsonValue,
  errors: string[]
): void => {
  if (!supportedAgentFields.has(field)) {
    errors.push(
      packageError(
        root,
        agentPath,
        `unsupported agent frontmatter field: ${field}`
      )
    );
  } else if (
    ["color", "effort", "memory", "model"].includes(field) &&
    typeof value !== "string"
  ) {
    errors.push(packageError(root, agentPath, `${field} must be a string`));
  } else if (
    ["disallowedTools", "skills", "tools"].includes(field) &&
    !isStringList(value)
  ) {
    errors.push(
      packageError(root, agentPath, `${field} must be a string or string array`)
    );
  } else if (field === "background" && typeof value !== "boolean") {
    errors.push(packageError(root, agentPath, "background must be a boolean"));
  } else if (
    field === "maxTurns" &&
    (typeof value !== "number" || !Number.isInteger(value) || value < 1)
  ) {
    errors.push(
      packageError(root, agentPath, "maxTurns must be a positive integer")
    );
  } else if (field === "isolation" && value !== "worktree") {
    errors.push(packageError(root, agentPath, "isolation must be worktree"));
  }
};

/** Validate one packaged agent. */
const validateAgent = (
  root: string,
  agentPath: string,
  stem: string,
  errors: string[]
): void => {
  const frontmatter = parseFrontmatter(agentPath);
  const { description, name } = frontmatter;
  if (!kebabPattern.test(stem) || name !== stem) {
    errors.push(
      packageError(
        root,
        agentPath,
        "frontmatter name must match the kebab-case agent filename stem"
      )
    );
  }
  if (typeof description !== "string" || description === "") {
    errors.push(
      packageError(root, agentPath, "description must be a non-empty string")
    );
  }
  for (const [field, value] of jsonEntries(frontmatter)) {
    validateAgentField(root, agentPath, field, value, errors);
  }
};

/** Validate every packaged agent and current frontmatter fields. */
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
    validateAgent(root, agentPath, path.basename(entry.name, ".md"), errors);
  }
};

/** Extract one second-level Markdown section. */
const extractSection = (text: string, heading: string): string => {
  const start = text.indexOf(`${heading}\n`);
  if (start === -1) {
    return "";
  }
  const rest = text.slice(start + heading.length + 1);
  const nextHeading = rest.search(/^## /mu);
  return nextHeading < 0 ? rest : rest.slice(0, nextHeading);
};

/** Return names listed in the first matching README inventory section. */
const listedInventory = (
  text: string,
  headings: readonly string[]
): Set<string> => {
  const section = headings
    .map((heading) => extractSection(text, heading))
    .find((candidate) => candidate !== "");
  const names = new Set<string>();
  if (section === undefined) {
    return names;
  }
  for (const line of section.split(/\r?\n/u)) {
    const bullet =
      /^\s*-\s+`?(?<name>[a-z0-9]+(?:-[a-z0-9]+)*)`?(?:\s*:|\s+-)/u.exec(line);
    if (bullet?.groups?.["name"] !== undefined) {
      names.add(bullet.groups["name"]);
    }
    if (line.trim().startsWith("|")) {
      const firstCell = line
        .split("|")
        .slice(1)[0]
        ?.trim()
        .replaceAll(/^`|`$/gu, "");
      if (
        firstCell !== undefined &&
        firstCell !== "---" &&
        kebabPattern.test(firstCell)
      ) {
        names.add(firstCell);
      }
    }
  }
  return names;
};

/** Validate one README inventory against the filesystem. */
const validateInventory = (
  root: string,
  readmePath: string,
  label: string,
  actual: ReadonlySet<string>,
  listed: ReadonlySet<string>,
  errors: string[]
): void => {
  const missing = [...actual].filter((name) => !listed.has(name)).toSorted();
  const extra = [...listed].filter((name) => !actual.has(name)).toSorted();
  if (
    actual.size > 0 &&
    (listed.size === 0 || missing.length > 0 || extra.length > 0)
  ) {
    errors.push(
      packageError(
        root,
        readmePath,
        `${label} inventory drift; missing: ${missing.join(", ") || "none"}; extra: ${extra.join(", ") || "none"}`
      )
    );
  }
};

/** Validate README purpose, runtime, layout, scope, skill, and agent alignment. */
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
  const text = readFileSync(readmePath, "utf-8");
  const frontmatter = parseFrontmatter(readmePath);
  if (
    typeof frontmatter["description"] !== "string" ||
    frontmatter["description"] === ""
  ) {
    errors.push(
      packageError(
        root,
        readmePath,
        "README description must be a non-empty string"
      )
    );
  }
  for (const heading of [
    "## Purpose",
    "## Runtime Model",
    "## Scope Notes"
  ] as const) {
    if (!text.includes(`${heading}\n`)) {
      errors.push(
        packageError(root, readmePath, `missing required section ${heading}`)
      );
    }
  }
  if (!text.includes("## Plugin Layout\n") && !text.includes("## Layout\n")) {
    errors.push(
      packageError(root, readmePath, "missing required layout section")
    );
  }
  const actualSkills = new Set(skillDirectories(pluginRoot));
  const actualAgents = new Set<string>();
  const agentsDir = path.join(pluginRoot, "agents");
  if (existsSync(agentsDir)) {
    for (const entry of readdirSync(agentsDir, { withFileTypes: true })) {
      if (entry.isFile() && entry.name.endsWith(".md")) {
        actualAgents.add(entry.name.slice(0, -".md".length));
      }
    }
  }
  validateInventory(
    root,
    readmePath,
    "Included Skills",
    actualSkills,
    listedInventory(text, ["## Included Skills", "## Included Skill"]),
    errors
  );
  validateInventory(
    root,
    readmePath,
    "Included Agents",
    actualAgents,
    listedInventory(text, [
      "## Included Agents",
      "## Included Agent",
      "## Plugin-Owned Structural Agents"
    ]),
    errors
  );
};

/** Validate plugin-local AGENTS and CLAUDE pointer files. */
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

/** Discover every plugin root that ships a Claude manifest. */
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

/** Validate one plugin license against the repository's canonical texts. */
const validateLicense = (
  root: string,
  pluginRoot: string,
  manifest: Record<string, JsonValue>,
  errors: string[]
): void => {
  const { license } = manifest;
  if (typeof license !== "string" || license === "") {
    return;
  }
  const localLicense = path.join(pluginRoot, "LICENSE");
  if (license === "MIT") {
    const effectiveLicense = existsSync(localLicense)
      ? localLicense
      : path.join(root, "LICENSE");
    if (sha256(readFileSync(effectiveLicense, "utf-8")) !== mitLicenseSha256) {
      errors.push(
        packageError(
          root,
          effectiveLicense,
          "must contain the canonical repository MIT license"
        )
      );
    }
    return;
  }
  if (!existsSync(localLicense)) {
    errors.push(
      packageError(
        root,
        localLicense,
        `${license} plugin requires a local LICENSE file`
      )
    );
    return;
  }
  if (
    license === "Apache-2.0" &&
    sha256(readFileSync(localLicense, "utf-8")) !== apacheLicenseSha256
  ) {
    errors.push(
      packageError(
        root,
        localLicense,
        "must contain the canonical Apache-2.0 text"
      )
    );
  }
};

/** Validate catalog coverage and manifest metadata parity. */
const validateMarketplace = (
  root: string,
  manifestedRoots: readonly string[],
  manifests: ReadonlyMap<string, Record<string, JsonValue>>,
  errors: string[]
): void => {
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
  const seenNames = new Set<string>();
  const seenRoots = new Set<string>();
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
    const { name, source } = entry;
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
    if (seenNames.has(name)) {
      errors.push(
        packageError(root, marketplacePath, `duplicate plugin name: ${name}`)
      );
    }
    seenNames.add(name);
    const pluginPath = path.resolve(root, source);
    if (!source.startsWith("./plugins/") || !pathExists(pluginPath)) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin source does not exist: ${source}`
        )
      );
      continue;
    }
    let pluginRealPath: string;
    try {
      pluginRealPath = realpathSync(pluginPath);
    } catch (error) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin source cannot be resolved: ${source}: ${
            error instanceof Error ? error.message : String(error)
          }`
        )
      );
      continue;
    }
    if (
      !isInsidePath(realpathSync(path.join(root, "plugins")), pluginRealPath)
    ) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin source escapes plugins/: ${source}`
        )
      );
      continue;
    }
    if (seenRoots.has(pluginRealPath)) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `duplicate plugin source: ${source}`
        )
      );
      continue;
    }
    seenRoots.add(pluginRealPath);
    if (name !== path.basename(pluginRealPath)) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin name ${name} does not match source basename ${path.basename(pluginRealPath)}`
        )
      );
    }
    const manifest = manifests.get(pluginRealPath);
    if (manifest === undefined) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `plugin source lacks manifest: ${source}`
        )
      );
      continue;
    }
    for (const field of ["description", "license"] as const) {
      if (entry[field] !== manifest[field]) {
        errors.push(
          packageError(
            root,
            marketplacePath,
            `${name} ${field} must match plugin.json exactly`
          )
        );
      }
    }
  }
  for (const pluginRoot of manifestedRoots) {
    const realRoot = realpathSync(pluginRoot);
    if (!seenRoots.has(realRoot)) {
      errors.push(
        packageError(
          root,
          marketplacePath,
          `manifested plugin is missing from catalog: ${path.basename(pluginRoot)}`
        )
      );
    }
  }
};

/** Validate marketplace package metadata and filesystem contracts. */
export const checkPackageSurface = (root: string): void => {
  const repositoryRoot = path.resolve(root, "..", "..");
  const errors: string[] = [];
  validateRootLayout(repositoryRoot, errors);
  const roots = pluginRoots(repositoryRoot);
  const manifests = new Map<string, Record<string, JsonValue>>();
  for (const pluginRoot of roots) {
    const manifest = validateManifest(repositoryRoot, pluginRoot, errors);
    manifests.set(realpathSync(pluginRoot), manifest);
    validateSkills(repositoryRoot, pluginRoot, errors);
    validateAgents(repositoryRoot, pluginRoot, errors);
    validateReadme(repositoryRoot, pluginRoot, errors);
    validatePluginAgentRules(repositoryRoot, pluginRoot, errors);
    validateLicense(repositoryRoot, pluginRoot, manifest, errors);
  }
  validateMarketplace(repositoryRoot, roots, manifests, errors);
  if (errors.length > 0) {
    throw new Error(`Plugin package validation failed:\n${errors.join("\n")}`);
  }
  console.error("[plugin package surface] OK");
};
