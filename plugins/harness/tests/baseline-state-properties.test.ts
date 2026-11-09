import {
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  writeFile
} from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { expect, onTestFinished, test } from "vitest";

import { configureBaseline } from "../scripts/configure.js";

test("conflicts at generated baseline destinations leave the target unchanged", async () => {
  const root = await mkdtemp(path.join(tmpdir(), "sinon-baseline-property-"));
  onTestFinished(() => rm(root, { force: true, recursive: true }));
  await Promise.all(
    (["node", "gradle", "uv"] as const).map(async (profile) => {
      const reference = path.join(root, `${profile}-reference`);
      await mkdir(reference);
      const baseline = await configureBaseline(reference, profile, "eligible");
      await Promise.all(
        baseline.created
          .filter((file) => file !== "gradle/libs.versions.toml")
          .map(async (relative, index) => {
            const target = path.join(root, `${profile}-${index}`);
            const conflict = path.join(target, relative);
            await mkdir(path.dirname(conflict), { recursive: true });
            await writeFile(conflict, "target-owned conflict\n");
            const before = await readdir(target, { recursive: true });
            await expect(
              configureBaseline(target, profile, "eligible")
            ).rejects.toThrow();
            expect(await readFile(conflict, "utf-8")).toBe(
              "target-owned conflict\n"
            );
            const after = await readdir(target, { recursive: true });
            expect(after.toSorted()).toEqual(before.toSorted());
          })
      );
    })
  );
});
