import { execFileSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { parseDocument } from "yaml";

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
const tree =
  revision === undefined
    ? undefined
    : execFileSync(
        "git",
        ["rev-parse", "--verify", "--end-of-options", `${revision}^{tree}`],
        repositoryOptions
      ).trim();
const files = execFileSync(
  "git",
  tree === undefined
    ? ["ls-files", "--cached", "-z"]
    : ["ls-tree", "-r", "--name-only", "-z", tree],
  repositoryOptions
).split("\0");
const modelPaths = files.filter((file) => file.endsWith(".tla"));
const modelNames = new Set(
  modelPaths.map((file) => path.basename(file, ".tla"))
);
const modelDirectories = new Set(modelPaths.map((file) => path.dirname(file)));
const chartExtensions = new Set([".mmd", ".mnd"]);
const scriptExtension = /^(?:\.[cm]?[jt]s|\.sh)$/u;
const modelScript = /[\w.-]+\.tla\b|\btla2tools\.jar\b|\btlc2\.TLC\b/u;
const peleImport =
  /(?:^[ \t]*import(?:\s+[\w$*{},\s]+?\s+from)?\s*|\b(?:import|require)\s*\(\s*)["']pele(?:\/[^"'\r\n]+)?["']/mu;

const readSource = (file: string): string =>
  execFileSync(
    "git",
    ["cat-file", "blob", `${tree ?? ""}:${file}`],
    repositoryOptions
  );

const matchesModelTitle = (file: string): boolean => {
  const frontMatter =
    /^\uFEFF?---[ \t]*\r?\n(?<yaml>[\s\S]*?)^---[ \t]*(?:\r?\n|$)/mu.exec(
      readSource(file)
    );
  const yaml = frontMatter?.groups?.yaml;
  if (frontMatter?.index !== 0 || yaml === undefined) {
    return false;
  }
  const document = parseDocument(yaml);
  const title: unknown = document.get("title");
  return (
    document.errors.length === 0 &&
    typeof title === "string" &&
    modelNames.has(title)
  );
};

const formalCharts = new Set<string>();
for (const file of files) {
  const ext = path.extname(file);
  if (
    chartExtensions.has(ext) &&
    (file.startsWith(skill) ||
      modelDirectories.has(path.dirname(file)) ||
      modelNames.has(path.basename(file, ext)) ||
      matchesModelTitle(file))
  ) {
    formalCharts.add(file.slice(0, -ext.length));
  }
}

for (const file of files) {
  const inSkill = file.startsWith(skill);
  const relative = inSkill ? file.slice(skill.length) : file;
  const ext = path.extname(file);
  const besideModel = modelDirectories.has(path.dirname(file));
  const formalChart = formalCharts.has(file.slice(0, -ext.length));
  let required: string | undefined;
  if (
    (ext === ".tla" ||
      (ext === ".cfg" &&
        (inSkill ||
          besideModel ||
          modelNames.has(path.basename(file, ext))))) &&
    !(inSkill && assetPath.test(relative))
  ) {
    required = "assets/<model>/";
  } else if (
    ((chartExtensions.has(ext) && formalChart) ||
      (ext === ".svg" && (inSkill || besideModel || formalChart))) &&
    !(inSkill && referencePath.test(relative))
  ) {
    required = "references/<model>/";
  } else if (
    scriptExtension.test(ext) &&
    !(inSkill && scriptPath.test(relative))
  ) {
    const source = inSkill ? "" : readSource(file);
    if (inSkill || modelScript.test(source) || peleImport.test(source)) {
      required = "scripts/ or scripts/<model>/ as TypeScript";
    }
  }
  if (required !== undefined) {
    console.error(`${file}: use ${skill}${required}`);
    process.exitCode = 1;
  }
}
