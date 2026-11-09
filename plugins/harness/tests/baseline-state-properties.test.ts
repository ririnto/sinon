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
  const results = await Promise.allSettled(
    (["node", "gradle", "uv"] as const).map(async (profile) => {
      const reference = path.join(root, `${profile}-reference`);
      await mkdir(reference);
      await writeFile(path.join(reference, "user-file"), "keep me\n");
      const baseline = await configureBaseline(reference, profile, "eligible");
      expect(baseline.created).not.toHaveLength(0);
      expect(await readFile(path.join(reference, "user-file"), "utf-8")).toBe(
        "keep me\n"
      );
      const cases = await Promise.allSettled(
        baseline.created
          .filter((file) => file !== "gradle/libs.versions.toml")
          .map(async (relative, index) => {
            const target = path.join(root, `${profile}-${index}`);
            const conflict = path.join(target, relative);
            await mkdir(path.dirname(conflict), { recursive: true });
            await writeFile(conflict, "target-owned conflict\n");
            await mkdir(path.join(target, "user-data"));
            await writeFile(
              path.join(target, "user-data", "notes"),
              "keep notes\n"
            );
            await writeFile(path.join(target, "user-file"), "keep me\n");
            const before = await readdir(target, { recursive: true });
            await expect(
              configureBaseline(target, profile, "eligible")
            ).rejects.toThrow(`Conflict at ${relative}`);
            expect(
              await readFile(path.join(target, "user-file"), "utf-8")
            ).toBe("keep me\n");
            expect(
              await readFile(path.join(target, "user-data", "notes"), "utf-8")
            ).toBe("keep notes\n");
            expect(await readFile(conflict, "utf-8")).toBe(
              "target-owned conflict\n"
            );
            const after = await readdir(target, { recursive: true });
            expect(after.toSorted()).toEqual(before.toSorted());
          })
      );
      for (const result of cases) {
        if (result.status === "rejected") {
          throw result.reason;
        }
      }
    })
  );
  for (const result of results) {
    if (result.status === "rejected") {
      throw result.reason;
    }
  }
});
