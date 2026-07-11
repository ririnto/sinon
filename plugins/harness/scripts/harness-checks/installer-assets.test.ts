import { test } from "bun:test";
import path from "node:path";

import { checkInstallerSecurityContract } from "./installer-assets.js";

test("installer assets state the supported atomic-write boundary", () => {
  checkInstallerSecurityContract(path.resolve(import.meta.dirname, "..", ".."));
});
