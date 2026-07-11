// -*- coding: utf-8 -*-

import { expect, test } from "bun:test";

import { requiredJavaReleaseForCheckstyle } from "./asset-versions.js";

test("Checkstyle major versions select their compatible Java release", () => {
  expect(requiredJavaReleaseForCheckstyle("12.1.0")).toBe(17);
  expect(requiredJavaReleaseForCheckstyle("13.0.1")).toBe(21);
  expect(() => requiredJavaReleaseForCheckstyle("invalid")).toThrow();
});
