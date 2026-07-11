// -*- coding: utf-8 -*-

import {
  accessSync,
  constants,
  existsSync,
  readFileSync,
  statSync
} from "node:fs";

export class CheckFailureError extends Error {
  name = "CheckFailureError";
}

export const fail = (message: string): never => {
  throw new CheckFailureError(message);
};

export const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null;

export const requireFile = (filePath: string): void => {
  if (!existsSync(filePath)) {
    fail(`[requireFile] missing required file: ${filePath}`);
  }
  if (!statSync(filePath).isFile()) {
    fail(`[requireFile] expected regular file: ${filePath}`);
  }
};

export const requireDir = (directoryPath: string): void => {
  if (!existsSync(directoryPath)) {
    fail(`[requireDir] missing required directory: ${directoryPath}`);
  }
  if (!statSync(directoryPath).isDirectory()) {
    fail(`[requireDir] expected directory: ${directoryPath}`);
  }
};

export const requireText = (filePath: string, fragment: string): void => {
  requireFile(filePath);
  if (!readFileSync(filePath, "utf-8").includes(fragment)) {
    fail(`[requireText] missing text in ${filePath}: ${fragment}`);
  }
};

export type TextCheck = Readonly<{
  fragments: readonly string[];
  path: string;
}>;

export const requireTexts = (checks: readonly TextCheck[]): void => {
  for (const check of checks) {
    for (const fragment of check.fragments) {
      requireText(check.path, fragment);
    }
  }
};

export const rejectTextFragments = (
  filePath: string,
  fragments: readonly string[]
): void => {
  requireFile(filePath);
  const content = readFileSync(filePath, "utf-8");
  for (const fragment of fragments) {
    if (content.includes(fragment)) {
      fail(`[rejectTextFragments] forbidden text in ${filePath}: ${fragment}`);
    }
  }
};

export const readRequiredCapture = (
  filePath: string,
  pattern: RegExp,
  label: string
): string => {
  requireFile(filePath);
  const match = readFileSync(filePath, "utf-8").match(pattern);
  if (match?.[1] === undefined) {
    return fail(`[assetVersion] missing ${label}: ${filePath}`);
  }
  return match[1];
};

export const requirePosixHook = (filePath: string): void => {
  requireFile(filePath);
  try {
    accessSync(filePath, constants.X_OK);
  } catch {
    fail(`[gitHook] hook must be executable: ${filePath}`);
  }
  if (
    !readFileSync(filePath, "utf-8").startsWith(
      "#!/usr/bin/env sh\n# -*- coding: utf-8 -*-\nset -e\n"
    )
  ) {
    fail(`[gitHook] hook must use the POSIX header: ${filePath}`);
  }
};
