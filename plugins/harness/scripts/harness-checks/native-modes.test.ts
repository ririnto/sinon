// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";
import {
  chmodSync,
  cpSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  rmSync,
  writeFileSync
} from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";

type NativeMode = "bun" | "gradle" | "maven" | "shell" | "uv";

const run = (
  command: readonly string[],
  cwd: string
): ReturnType<typeof Bun.spawnSync> =>
  Bun.spawnSync([...command], { cwd, stderr: "pipe", stdout: "pipe" });

const requireSuccess = (command: readonly string[], cwd: string): void => {
  const result = run(command, cwd);
  expect(
    result.success,
    `${command.join(" ")}\n${result.stdout?.toString() ?? ""}\n${result.stderr?.toString() ?? ""}`
  ).toBe(true);
};

const installArgs = (
  installer: string,
  target: string,
  mode: NativeMode
): readonly string[] => [
  process.execPath,
  installer,
  "--mode",
  mode,
  "--ci-host",
  "both",
  "--target",
  target
];

const requireHooksInactive = (target: string): void => {
  const configured = run(
    ["git", "config", "--local", "--get", "core.hooksPath"],
    target
  );
  expect(configured.success).toBe(false);
  expect(existsSync(path.join(target, ".git", "hooks", "pre-commit"))).toBe(
    false
  );
  expect(existsSync(path.join(target, ".git", "hooks", "pre-push"))).toBe(
    false
  );
};

const writeSystemWrapper = (
  target: string,
  name: string,
  command: string
): void => {
  const wrapper = path.join(target, name);
  writeFileSync(
    wrapper,
    `#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\n\nexec ${command} "$@"\n`,
    "utf-8"
  );
  chmodSync(wrapper, 0o755);
};

const nativeCommands = (
  mode: NativeMode
): Readonly<{
  check: readonly string[];
  setup?: readonly string[];
}> => {
  switch (mode) {
    case "gradle": {
      return {
        check: ["gradle", "ktlintCheck", "--no-daemon"],
        setup: ["gradle", "help", "--no-daemon"]
      };
    }
    case "maven": {
      return { check: ["mvn", "validate"] };
    }
    case "uv": {
      return {
        check: ["uv", "run", "scripts/check.py"],
        setup: ["uv", "sync"]
      };
    }
    case "bun": {
      return {
        check: ["bun", "run", "check"],
        setup: ["bun", "install"]
      };
    }
    case "shell": {
      return { check: ["sh", "scripts/check.sh"] };
    }
    default: {
      throw new Error(`unsupported native mode: ${mode}`);
    }
  }
};

test.skipIf(process.env["HARNESS_RUN_NATIVE_TESTS"] !== "1")(
  "fresh native checks keep setup inactive and explicit hooks pass",
  () => {
    for (const tool of [
      "bun",
      "git",
      "gradle",
      "mvn",
      "shellcheck",
      "shfmt",
      "uv"
    ]) {
      expect(
        Bun.which(tool),
        `${tool} is required for the native matrix`
      ).not.toBeNull();
    }
    const harnessRoot = path.resolve(import.meta.dirname, "..", "..");
    const temporaryRoot = mkdtempSync(path.join(tmpdir(), "harness-native-"));
    try {
      const skillsRoot = path.join(temporaryRoot, "cache", "skills");
      const installSkill = path.join(skillsRoot, "harness-install");
      const validateSkill = path.join(skillsRoot, "harness-validate");
      mkdirSync(skillsRoot, { recursive: true });
      cpSync(
        path.join(harnessRoot, "skills", "harness-install"),
        installSkill,
        {
          recursive: true
        }
      );
      cpSync(
        path.join(harnessRoot, "skills", "harness-validate"),
        validateSkill,
        {
          recursive: true
        }
      );
      const installer = path.join(
        installSkill,
        "scripts",
        "install-harness.ts"
      );
      const validator = path.join(
        validateSkill,
        "scripts",
        "validate-install-record.ts"
      );
      for (const mode of ["bun", "gradle", "maven", "shell", "uv"] as const) {
        const target = path.join(temporaryRoot, `${mode}-target`);
        mkdirSync(target);
        requireSuccess(["git", "init", "--quiet"], target);
        requireSuccess(installArgs(installer, target, mode), target);
        if (mode === "gradle") {
          writeSystemWrapper(target, "gradlew", "gradle");
        } else if (mode === "maven") {
          writeSystemWrapper(target, "mvnw", "mvn");
        }
        requireSuccess(["git", "add", "--all"], target);
        const commands = nativeCommands(mode);
        if (commands.setup !== undefined) {
          requireSuccess(commands.setup, target);
        }
        requireHooksInactive(target);
        requireSuccess(commands.check, target);
        requireSuccess([process.execPath, validator, target], target);
        requireSuccess(
          [...installArgs(installer, target, mode), "--activate-hooks"],
          target
        );
        expect(
          run(["git", "config", "--local", "--get", "core.hooksPath"], target)
            .stdout?.toString()
            .trim()
        ).toBe(".githooks/");
        requireSuccess([path.join(target, ".githooks", "pre-commit")], target);
        requireSuccess([path.join(target, ".githooks", "pre-push")], target);
      }
    } finally {
      rmSync(temporaryRoot, { force: true, recursive: true });
    }
  },
  600_000
);
