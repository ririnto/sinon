import { lstat, mkdir, readFile, realpath, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

interface CatalogAuthor {
  readonly name?: string;
}

interface CatalogPlugin {
  readonly author?: CatalogAuthor;
  readonly category?: string;
  readonly description?: string;
  readonly license?: string;
  readonly name: string;
  readonly source: unknown;
  readonly version?: string;
}

interface SourceCatalog {
  readonly name: string;
  readonly plugins: readonly CatalogPlugin[];
}

interface LocalSource {
  readonly source: "local";
  readonly path: string;
}

interface UrlSource {
  readonly source: "url";
  readonly url: string;
  readonly ref?: string;
  readonly sha?: string;
}

type PluginSource = LocalSource | UrlSource;

interface NativePlugin {
  readonly name: string;
  readonly source: PluginSource;
  readonly policy: {
    readonly installation: "AVAILABLE";
    readonly authentication: "ON_INSTALL";
  };
  readonly category: string;
}

interface NativeMarketplace {
  readonly name: string;
  readonly interface: {
    readonly displayName: "Sinon";
  };
  readonly plugins: readonly NativePlugin[];
}

const sourceCatalogPath = ".claude-plugin/marketplace.json";
const generatedCatalogPath = ".agents/plugins/marketplace.json";

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

const requiredString = (value: unknown, label: string): string => {
  if (typeof value !== "string" || value.length === 0) {
    throw new Error(`${label} must be a non-empty string`);
  }
  return value;
};

const parseCatalog = (value: unknown): SourceCatalog => {
  if (!isRecord(value) || !Array.isArray(value.plugins)) {
    throw new Error("The source marketplace must contain a plugins array");
  }
  const plugins = value.plugins.map((plugin, index): CatalogPlugin => {
    if (!isRecord(plugin)) {
      throw new Error(`Marketplace plugin at index ${index} must be an object`);
    }
    const author = isRecord(plugin.author)
      ? { name: plugin.author.name as string | undefined }
      : undefined;
    if (author?.name !== undefined && typeof author.name !== "string") {
      throw new Error(
        `Marketplace plugin at index ${index} has an invalid author name`
      );
    }
    return {
      author,
      category:
        plugin.category === undefined
          ? undefined
          : requiredString(
              plugin.category,
              `Marketplace plugin at index ${index} category`
            ),
      description:
        plugin.description === undefined
          ? undefined
          : requiredString(
              plugin.description,
              `Marketplace plugin at index ${index} description`
            ),
      license:
        plugin.license === undefined
          ? undefined
          : requiredString(
              plugin.license,
              `Marketplace plugin at index ${index} license`
            ),
      name: requiredString(
        plugin.name,
        `Marketplace plugin at index ${index} name`
      ),
      source: plugin.source,
      version:
        plugin.version === undefined
          ? undefined
          : requiredString(
              plugin.version,
              `Marketplace plugin at index ${index} version`
            )
    };
  });
  return {
    name: requiredString(value.name, "Marketplace name"),
    plugins
  };
};

const mapSource = (source: unknown, pluginName: string): PluginSource => {
  if (typeof source === "string") {
    if (!source.startsWith("./")) {
      throw new Error(`Local source for ${pluginName} must start with ./`);
    }
    return { path: source, source: "local" };
  }
  if (!isRecord(source)) {
    throw new Error(`Unsupported marketplace source for ${pluginName}`);
  }
  if (source.source !== "url") {
    throw new Error(
      `Unsupported marketplace source "${String(source.source)}" for ${pluginName}`
    );
  }
  const unsupportedKeys = Object.keys(source).filter(
    (key) => !["source", "url", "ref", "sha"].includes(key)
  );
  if (unsupportedKeys.length > 0) {
    throw new Error(
      `Unsupported URL source fields for ${pluginName}: ${unsupportedKeys.join(", ")}`
    );
  }
  const url = requiredString(source.url, `URL source for ${pluginName}`);
  if (source.ref !== undefined && typeof source.ref !== "string") {
    throw new Error(`URL ref for ${pluginName} must be a string`);
  }
  if (source.sha !== undefined && typeof source.sha !== "string") {
    throw new Error(`URL sha for ${pluginName} must be a string`);
  }
  if (source.ref !== undefined && source.sha !== undefined) {
    throw new Error(
      `URL source for ${pluginName} cannot specify both ref and sha`
    );
  }
  return {
    ...(source.ref === undefined ? {} : { ref: source.ref }),
    ...(source.sha === undefined ? {} : { sha: source.sha }),
    source: "url",
    url
  };
};

const isWithin = (rootPath: string, targetPath: string): boolean => {
  const pathFromRoot = path.relative(rootPath, targetPath);
  return (
    pathFromRoot.length > 0 &&
    pathFromRoot !== ".." &&
    !pathFromRoot.startsWith(`..${path.sep}`) &&
    !path.isAbsolute(pathFromRoot)
  );
};

const readManifest = async (
  manifestPath: string,
  label: string
): Promise<Record<string, unknown>> => {
  let manifest: unknown;
  try {
    manifest = JSON.parse(await readFile(manifestPath, "utf-8")) as unknown;
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    throw new Error(`Cannot read ${label}: ${reason}`, { cause: error });
  }
  if (!isRecord(manifest)) {
    throw new Error(`${label} must be an object`);
  }
  return manifest;
};

const assertManifestFieldMatches = (
  pluginName: string,
  field: string,
  expected: unknown,
  actual: unknown,
  expectedLabel: string,
  actualLabel: string
): void => {
  const expectedValue = requiredString(expected, `${expectedLabel} ${field}`);
  const actualValue = requiredString(actual, `${actualLabel} ${field}`);
  if (expectedValue !== actualValue) {
    throw new Error(
      `${expectedLabel} ${field} for ${pluginName} does not match ${actualLabel}`
    );
  }
};

const validateCatalogMetadata = (
  plugin: CatalogPlugin,
  manifest: Record<string, unknown>
): void => {
  for (const field of ["name", "version"] as const) {
    assertManifestFieldMatches(
      plugin.name,
      field,
      plugin[field],
      manifest[field],
      "Source marketplace",
      "native manifest"
    );
  }
  if (plugin.license !== undefined) {
    assertManifestFieldMatches(
      plugin.name,
      "license",
      plugin.license,
      manifest.license,
      "Source marketplace",
      "native manifest"
    );
  }
  if (plugin.author?.name !== undefined) {
    if (!isRecord(manifest.author)) {
      throw new Error(
        `Portable manifest for ${plugin.name} is missing its author`
      );
    }
    assertManifestFieldMatches(
      plugin.name,
      "author name",
      plugin.author.name,
      manifest.author.name,
      "Source marketplace",
      "native manifest"
    );
  }
};

const validatePackageMetadata = (
  pluginName: string,
  manifest: Record<string, unknown>,
  claudeManifest: Record<string, unknown>
): void => {
  for (const field of ["name", "version", "description"] as const) {
    assertManifestFieldMatches(
      pluginName,
      field,
      manifest[field],
      claudeManifest[field],
      "Native manifest",
      "Claude manifest"
    );
  }
  if (manifest.license !== undefined || claudeManifest.license !== undefined) {
    assertManifestFieldMatches(
      pluginName,
      "license",
      manifest.license,
      claudeManifest.license,
      "Native manifest",
      "Claude manifest"
    );
  }
  if (manifest.author !== undefined || claudeManifest.author !== undefined) {
    if (!isRecord(manifest.author) || !isRecord(claudeManifest.author)) {
      throw new Error(
        `Native and Claude manifest authors for ${pluginName} do not match`
      );
    }
    assertManifestFieldMatches(
      pluginName,
      "author name",
      manifest.author.name,
      claudeManifest.author.name,
      "Native manifest",
      "Claude manifest"
    );
  }
};

const assertNoRootPluginManifest = async (
  pluginName: string,
  pluginPath: string
): Promise<void> => {
  try {
    await lstat(path.resolve(pluginPath, "plugin.json"));
  } catch (error) {
    if (isRecord(error) && error.code === "ENOENT") {
      return;
    }
    const reason = error instanceof Error ? error.message : String(error);
    throw new Error(
      `Cannot check root plugin manifest for ${pluginName}: ${reason}`,
      {
        cause: error
      }
    );
  }
  throw new Error(
    `Root plugin.json is not allowed for ${pluginName}; use .codex-plugin/plugin.json`
  );
};

const resolveLocalPluginPath = async (
  repositoryRoot: string,
  rootRealPath: string,
  pluginName: string,
  source: LocalSource
): Promise<string> => {
  const unresolvedPluginPath = path.resolve(repositoryRoot, source.path);
  if (!isWithin(repositoryRoot, unresolvedPluginPath)) {
    throw new Error(
      `Local source for ${pluginName} escapes the repository root`
    );
  }
  const pluginPath = await realpath(unresolvedPluginPath);
  if (!isWithin(rootRealPath, pluginPath)) {
    throw new Error(
      `Local source for ${pluginName} resolves outside the repository root`
    );
  }
  return pluginPath;
};

const validateLocalPlugin = async (
  plugin: CatalogPlugin,
  pluginPath: string
): Promise<void> => {
  await assertNoRootPluginManifest(plugin.name, pluginPath);
  const manifest = await readManifest(
    path.resolve(pluginPath, ".codex-plugin/plugin.json"),
    `native manifest for ${plugin.name}`
  );
  requiredString(
    manifest.description,
    `Native manifest for ${plugin.name} description`
  );
  const claudeManifest = await readManifest(
    path.resolve(pluginPath, ".claude-plugin/plugin.json"),
    `Claude manifest for ${plugin.name}`
  );
  validateCatalogMetadata(plugin, manifest);
  validatePackageMetadata(plugin.name, manifest, claudeManifest);
};

const categoryLabel = (category: string | undefined): string => {
  if (category === undefined) {
    return "Development";
  }
  return category.charAt(0).toUpperCase() + category.slice(1);
};

/**
 * Builds a native Codex marketplace from the authoritative Claude catalog.
 * Local plugin manifests and real paths are checked before the result is returned.
 */
export const createCodexMarketplace = async (
  catalogValue: unknown,
  repositoryRoot: string
): Promise<NativeMarketplace> => {
  const catalog = parseCatalog(catalogValue);
  const sources = catalog.plugins.map((plugin) =>
    mapSource(plugin.source, plugin.name)
  );
  const seenNames = new Set<string>();
  const seenPaths = new Set<string>();
  for (const [index, plugin] of catalog.plugins.entries()) {
    if (seenNames.has(plugin.name)) {
      throw new Error(`Duplicate marketplace plugin name: ${plugin.name}`);
    }
    seenNames.add(plugin.name);
    const source = sources[index];
    if (source?.source === "local") {
      if (seenPaths.has(source.path)) {
        throw new Error(
          `Duplicate local marketplace source path: ${source.path}`
        );
      }
      seenPaths.add(source.path);
    }
  }
  const rootRealPath = await realpath(repositoryRoot);
  const localPluginPaths = await Promise.all(
    catalog.plugins.map((plugin, index) => {
      const source = sources[index];
      return source?.source === "local"
        ? resolveLocalPluginPath(
            repositoryRoot,
            rootRealPath,
            plugin.name,
            source
          )
        : undefined;
    })
  );
  const seenRealPaths = new Set<string>();
  for (const [index, pluginPath] of localPluginPaths.entries()) {
    if (pluginPath !== undefined && seenRealPaths.has(pluginPath)) {
      throw new Error(
        `Duplicate local marketplace plugin directory for ${catalog.plugins[index]?.name}`
      );
    }
    if (pluginPath !== undefined) {
      seenRealPaths.add(pluginPath);
    }
  }
  await Promise.all(
    catalog.plugins.map((plugin, index) => {
      const pluginPath = localPluginPaths[index];
      return pluginPath === undefined
        ? null
        : validateLocalPlugin(plugin, pluginPath);
    })
  );
  const plugins = catalog.plugins.map((plugin, index): NativePlugin => ({
    category: categoryLabel(plugin.category),
    name: plugin.name,
    policy: {
      authentication: "ON_INSTALL",
      installation: "AVAILABLE"
    },
    source: sources[index] as PluginSource
  }));
  return {
    interface: { displayName: "Sinon" },
    name: catalog.name,
    plugins
  };
};

/**
 * Generates the checked-in marketplace or verifies that it matches current inputs.
 * Check mode reads the generated file and reports drift without writing files.
 */
export const syncCodexMarketplace = async (
  catalogValue: unknown,
  repositoryRoot: string,
  checkOnly: boolean
): Promise<void> => {
  const marketplace = await createCodexMarketplace(
    catalogValue,
    repositoryRoot
  );
  const outputPath = path.resolve(repositoryRoot, generatedCatalogPath);
  const expected = `${JSON.stringify(marketplace, null, 2)}\n`;
  if (checkOnly) {
    let current: string;
    try {
      current = await readFile(outputPath, "utf-8");
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      throw new Error(`Cannot check generated marketplace: ${reason}`, {
        cause: error
      });
    }
    if (current !== expected) {
      throw new Error(
        `${generatedCatalogPath} is out of date; run the generator to refresh it`
      );
    }
  } else {
    await mkdir(path.dirname(outputPath), { recursive: true });
    await writeFile(outputPath, expected, "utf-8");
  }
};

const runCli = async (): Promise<void> => {
  const args = process.argv.slice(2);
  if (args.length > 1 || (args.length === 1 && args[0] !== "--check")) {
    throw new Error("Usage: tsx scripts/marketplace/generate.ts [--check]");
  }
  const repositoryRoot = fileURLToPath(new URL("../../", import.meta.url));
  const catalog = JSON.parse(
    await readFile(path.resolve(repositoryRoot, sourceCatalogPath), "utf-8")
  ) as unknown;
  await syncCodexMarketplace(catalog, repositoryRoot, args[0] === "--check");
  process.stdout.write(
    args[0] === "--check"
      ? "Native Codex marketplace is current.\n"
      : "Generated .agents/plugins/marketplace.json.\n"
  );
};

if (
  process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href
) {
  try {
    await runCli();
  } catch (error) {
    process.stderr.write(
      `${error instanceof Error ? error.message : String(error)}\n`
    );
    process.exitCode = 1;
  }
}
