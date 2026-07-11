// -*- coding: utf-8 -*-

import { fail } from "./check-support.js";

/** Return the Java release required by the declared Checkstyle major line. */
export const requiredJavaReleaseForCheckstyle = (version: string): number => {
  const major = Number(version.split(".")[0]);
  if (!Number.isInteger(major) || major < 1) {
    return fail(`[assetVersion] invalid Checkstyle version: ${version}`);
  }
  return major >= 13 ? 21 : 17;
};
