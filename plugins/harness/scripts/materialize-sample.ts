import { cp, lstat, mkdir, readdir, realpath } from "node:fs/promises";
import path from "node:path";
import { parseArgs } from "node:util";

const samples = [
  "cargo-workspace",
  "go-workspace",
  "gradle-multi-module",
  "maven-reactor",
  "pnpm-workspace",
  "uv-workspace"
] as const;

type Sample = (typeof samples)[number];

const generated = new Set([
  ".git",
  ".gradle",
  ".ruff_cache",
  ".venv",
  "__pycache__",
  "build",
  "dist",
  "node_modules",
  "target"
]);

const ignored = (relative: string): boolean =>
  relative.split(path.sep).some((part) => generated.has(part)) ||
  relative === path.join(".husky", "_") ||
  relative.startsWith(`${path.join(".husky", "_")}${path.sep}`);

const inspectDirectory = async (
  directory: string,
  input: string
): Promise<void> => {
  const entries = await readdir(directory, { withFileTypes: true });
  await Promise.all(
    entries.map(async (sourceEntry) => {
      const sourcePath = path.join(directory, sourceEntry.name);
      const relative = path.relative(input, sourcePath);
      if (!ignored(relative)) {
        if (sourceEntry.isDirectory()) {
          await inspectDirectory(sourcePath, input);
        } else if (!sourceEntry.isFile()) {
          throw new Error(`Unsupported sample entry: ${relative}`);
        }
      }
    })
  );
};

const includeEntry = async (
  input: string,
  entryPath: string
): Promise<boolean> => {
  const relative = path.relative(input, entryPath);
  if (ignored(relative)) {
    return false;
  }
  const entry = await lstat(entryPath);
  if (!entry.isDirectory() && !entry.isFile()) {
    throw new Error(`Unsupported sample entry: ${relative}`);
  }
  return true;
};

/**
 * Copy an independent sample into an existing empty directory.
 * The destination must be a regular directory and the sample may not contain symlinks.
 */
export const materializeSample = async (
  name: Sample,
  target: string
): Promise<void> => {
  if (!samples.includes(name)) {
    throw new Error(`Unknown sample: ${name}`);
  }
  const root = path.resolve(target);
  const entry = await lstat(root);
  if (!entry.isDirectory() || entry.isSymbolicLink()) {
    throw new Error("The target must be a regular directory.");
  }
  const existing = await readdir(root);
  if (existing.length !== 0) {
    throw new Error("The target directory must be empty.");
  }
  const source = path.resolve(import.meta.dirname, "../samples", name);
  const kotlin = path.resolve(import.meta.dirname, "../tooling/kotlin");
  await Promise.all(
    (name === "gradle-multi-module" ? [source, kotlin] : [source]).map(
      (input) => inspectDirectory(input, input)
    )
  );
  const children = await readdir(source);
  await Promise.all(
    children.map((child) =>
      cp(path.join(source, child), path.join(root, child), {
        errorOnExist: true,
        filter: includeEntry.bind(null, source),
        force: false,
        recursive: true
      })
    )
  );
  if (name === "gradle-multi-module") {
    await mkdir(path.join(root, "tooling"), { recursive: true });
    await cp(kotlin, path.join(root, "tooling/kotlin"), {
      errorOnExist: true,
      filter: includeEntry.bind(null, kotlin),
      force: false,
      recursive: true
    });
  }
};

if (
  process.argv[1] &&
  (await realpath(process.argv[1])) === import.meta.filename
) {
  const { values } = parseArgs({
    options: {
      help: { type: "boolean" },
      sample: { type: "string" },
      target: { type: "string" }
    }
  });
  if (values.help) {
    process.stdout.write(
      `Usage: node materialize-sample.ts --sample <${samples.join("|")}> --target <empty-directory>\n`
    );
  } else {
    if (
      !values.sample ||
      !samples.includes(values.sample as Sample) ||
      !values.target
    ) {
      throw new Error(
        "Provide a supported --sample and an empty --target directory."
      );
    }
    await materializeSample(values.sample as Sample, values.target);
  }
}
