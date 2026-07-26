import { lstatSync, realpathSync } from "node:fs";
import path from "node:path";

interface HookInput {
  tool_input?: {
    file_path?: unknown;
  };
  tool_name?: unknown;
}

const SECRET_FILE_NAMES = new Set(["credentials.json", "secrets"]);

/** Return true when an error reports a missing filesystem entry. */
const isMissingPathError = (error: unknown): boolean =>
  error instanceof Error &&
  "code" in error &&
  (error as NodeJS.ErrnoException).code === "ENOENT";

/** Return true when a candidate path stays inside an allowed root. */
const isInside = (root: string, candidate: string): boolean => {
  const fromRoot = path.relative(root, candidate);
  return !(
    path.isAbsolute(fromRoot) ||
    fromRoot === ".." ||
    fromRoot.startsWith(`..${path.sep}`)
  );
};

/** Resolve an existing target or the real parent of a new target. */
const resolveWriteTarget = (projectRoot: string, filePath: string): string => {
  const requested = path.resolve(projectRoot, filePath);
  try {
    lstatSync(requested);
  } catch (error) {
    if (!isMissingPathError(error)) {
      throw error;
    }
    const realParent = realpathSync(path.dirname(requested));
    return path.join(realParent, path.basename(requested));
  }
  return realpathSync(requested);
};

/** Return true for secret-bearing filenames that this starter blocks. */
const isSecretFile = (filePath: string): boolean => {
  const name = path.basename(filePath);
  return (
    name === ".env" || name.startsWith(".env.") || SECRET_FILE_NAMES.has(name)
  );
};

let input: HookInput;
try {
  input = JSON.parse(await Bun.stdin.text()) as HookInput;
} catch {
  console.error("Blocked request with invalid hook JSON.");
  process.exit(2);
}

if (input.tool_name !== "Write" && input.tool_name !== "Edit") {
  process.exit(0);
}

const filePath = input.tool_input?.file_path;
const projectDirectory = process.env["CLAUDE_PROJECT_DIR"];
if (typeof filePath !== "string" || projectDirectory === undefined) {
  console.error("Blocked request with a missing file path or project root.");
  process.exit(2);
}
if (isSecretFile(filePath)) {
  console.error("Blocked request that targets a secret-bearing filename.");
  process.exit(2);
}

try {
  const projectRoot = realpathSync(projectDirectory);
  const target = resolveWriteTarget(projectRoot, filePath);
  if (!isInside(projectRoot, target)) {
    console.error(
      "Blocked request whose target resolves outside the project root."
    );
    process.exit(2);
  }
} catch {
  console.error("Blocked request whose target cannot be resolved safely.");
  process.exit(2);
}
