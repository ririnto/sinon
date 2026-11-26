import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const skill = ".claude/skills/formal-verification/";
const modelDirectory = "[a-z0-9]+(?:-[a-z0-9]+)*";
const assetPath = new RegExp(
  `^assets/${modelDirectory}/[^/]+\\.(?:cfg|tla)$`,
  "u"
);
const referencePath = new RegExp(
  `^references/${modelDirectory}/[^/]+\\.(?:mmd|svg)$`,
  "u"
);
const scriptPath = new RegExp(
  `^scripts/(?:${modelDirectory}/)?[^/]+\\.ts$`,
  "u"
);

const repositoryOptions = {
  cwd: fileURLToPath(new URL("../../../../", import.meta.url)),
  encoding: "utf-8" as const
};
const [revision] = process.argv.slice(2);
const files = (
  revision === undefined
    ? execFileSync("git", ["ls-files", "--cached", "-z"], repositoryOptions)
    : execFileSync(
        "git",
        [
          "ls-tree",
          "-r",
          "--name-only",
          "-z",
          execFileSync(
            "git",
            ["rev-parse", "--verify", "--end-of-options", `${revision}^{tree}`],
            repositoryOptions
          ).trim()
        ],
        repositoryOptions
      )
).split("\0");
const modelNames = new Set(
  files
    .filter((file) => file.endsWith(".tla"))
    .map((file) => path.basename(file, ".tla"))
);
const chartNames = new Set(
  files
    .filter((file) => file.endsWith(".mmd"))
    .map((file) => path.basename(file, ".mmd"))
);

for (const file of files) {
  const inSkill = file.startsWith(skill);
  const relative = inSkill ? file.slice(skill.length) : file;
  const ext = path.extname(file);
  const name = path.basename(file, ext);
  const formalArea =
    inSkill ||
    file.startsWith("verification/") ||
    file.startsWith("plugins/observability-assets/tests/promtool/model/");
  let required: string | undefined;
  if (
    (ext === ".tla" ||
      (ext === ".cfg" && (formalArea || modelNames.has(name)))) &&
    !(inSkill && assetPath.test(relative))
  ) {
    required = "assets/<model>/";
  } else if (
    (ext === ".mmd" ||
      ext === ".mnd" ||
      (ext === ".svg" &&
        (formalArea || chartNames.has(name) || name.includes("statechart")))) &&
    !(inSkill && referencePath.test(relative))
  ) {
    required = "references/<model>/";
  } else if (
    /^(?:\.[cm]?[jt]s|\.sh)$/u.test(ext) &&
    (formalArea ||
      name.includes("statechart") ||
      name.endsWith("-lifecycle.view")) &&
    !(inSkill && scriptPath.test(relative))
  ) {
    required = "scripts/ or scripts/<model>/ as TypeScript";
  }
  if (required !== undefined) {
    console.error(`${file}: use ${skill}${required}`);
    process.exitCode = 1;
  }
}
