import { readFile } from "node:fs/promises";
import path from "node:path";

export const pluginRoot = path.resolve(import.meta.dirname, "..");

export const readText = async (relativePath) =>
  await readFile(path.resolve(pluginRoot, relativePath), "utf-8");

export const readJson = async (relativePath) =>
  JSON.parse(await readText(relativePath));

export const parseFrontmatter = (markdown) => {
  const match = markdown.match(/^---\r?\n(?<fields>[\s\S]*?)\r?\n---\r?\n/u);
  if (!match) {
    throw new Error("missing YAML frontmatter");
  }
  const fields = {};
  for (const line of match.groups.fields.split(/\r?\n/u)) {
    const separator = line.indexOf(":");
    if (separator === -1 || /^\s/u.test(line)) {
      continue;
    }
    fields[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
  }
  return {
    body: markdown.slice(match[0].length),
    fields
  };
};
