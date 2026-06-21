import { ciHostFromValue, modeFromValue } from "./commands.js";
import { runInstaller } from "./installer.js";
import { fail } from "./types.js";
import type { Action, InstallerConfig } from "./types.js";

type ParsedFlags = Readonly<{
  ciHost: null | string;
  force: boolean;
  mode: null | string;
  noCi: boolean;
  only: null | string;
  preview: boolean;
  show: null | string;
  target: string;
}>;

const requireValue = (
  argv: readonly string[],
  index: number,
  flag: string
): string => {
  const value = argv[index + 1];
  if (value === undefined || value.startsWith("--")) {
    return fail(`argument ${flag}: expected one argument`, 2);
  }
  return value;
};

const parseFlags = (argv: readonly string[]): ParsedFlags => {
  const mutable = {
    ciHost: null as null | string,
    force: false,
    mode: null as null | string,
    noCi: false,
    only: null as null | string,
    preview: false,
    show: null as null | string,
    target: process.env["HARNESS_TARGET_ROOT"] ?? "."
  };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    switch (arg) {
      case "--mode": {
        mutable.mode = requireValue(argv, index, arg);
        index += 1;
        break;
      }
      case "--ci-host": {
        mutable.ciHost = requireValue(argv, index, arg);
        index += 1;
        break;
      }
      case "--target": {
        mutable.target = requireValue(argv, index, arg);
        index += 1;
        break;
      }
      case "--force": {
        mutable.force = true;
        break;
      }
      case "--no-ci": {
        mutable.noCi = true;
        break;
      }
      case "--preview": {
        mutable.preview = true;
        break;
      }
      case "--show": {
        mutable.show = requireValue(argv, index, arg);
        index += 1;
        break;
      }
      case "--only": {
        mutable.only = requireValue(argv, index, arg);
        index += 1;
        break;
      }
      default: {
        return fail(`unrecognized arguments: ${arg}`, 2);
      }
    }
  }
  return mutable;
};

const selectedAction = (flags: ParsedFlags): Action => {
  const selected = [
    flags.preview,
    flags.show !== null,
    flags.only !== null
  ].filter(Boolean).length;
  if (selected > 1) {
    return fail(
      "argument --show/--only/--preview: not allowed with another action",
      2
    );
  }
  if (flags.preview) {
    return "preview";
  }
  if (flags.show !== null) {
    return "show";
  }
  if (flags.only !== null) {
    return "only";
  }
  return "install";
};

const modeCiHost = (flags: ParsedFlags): InstallerConfig["ciHost"] => {
  const rawCiHost = flags.ciHost;
  if (rawCiHost === null) {
    return fail("--ci-host is required (github|gitlab|both|none).", 2);
  }
  if (flags.noCi) {
    return "none";
  }
  return ciHostFromValue(rawCiHost);
};

const selectedPathForAction = (
  action: Action,
  flags: ParsedFlags
): null | string => {
  switch (action) {
    case "show": {
      if (flags.show !== null) {
        return flags.show;
      }
      return fail("--show requires a path argument.", 2);
    }
    case "only": {
      if (flags.only !== null) {
        return flags.only;
      }
      return fail("--only requires a path argument.", 2);
    }
    case "install":
    case "preview": {
      return null;
    }
    default: {
      return fail(`unsupported action: ${action}`);
    }
  }
};

/** Parse CLI flags into an installer config. */
export const parseArgs = (argv: readonly string[]): InstallerConfig => {
  const parsed = parseFlags(argv);
  if (parsed.mode === null) {
    return fail("--mode is required (gradle|maven|uv|bun|shell).", 2);
  }
  if (parsed.ciHost === null) {
    return fail("--ci-host is required (github|gitlab|both|none).", 2);
  }
  if (parsed.noCi && parsed.ciHost !== "none") {
    return fail("--no-ci cannot be combined with --ci-host other than none", 2);
  }
  const action = selectedAction(parsed);
  return {
    action,
    ciHost: modeCiHost(parsed),
    force: parsed.force,
    mode: modeFromValue(parsed.mode),
    selectedPath: selectedPathForAction(action, parsed),
    targetRoot: parsed.target
  };
};

/** Run the installer CLI and return a process exit code. */
export const main = async (argv: readonly string[]): Promise<number> => {
  await runInstaller(parseArgs(argv));
  return 0;
};
