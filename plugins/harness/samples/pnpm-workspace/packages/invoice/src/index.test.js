import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { test } from "node:test";

import { invoiceTotal } from "./index.js";

await test("totals multiple lines using the workspace money package", () => {
  assert.equal(
    invoiceTotal([
      { quantity: 3, unitCents: 199 },
      { quantity: 2, unitCents: 75 }
    ]),
    747
  );
  assert.throws(() => invoiceTotal([{ quantity: 1, unitCents: -1 }]));
  assert.throws(
    () =>
      invoiceTotal([
        { quantity: 1, unitCents: Number.MAX_SAFE_INTEGER },
        { quantity: 1, unitCents: 1 }
      ]),
    RangeError
  );
});

await test("bundled CLI reports the total and rejects invalid input", () => {
  const result = spawnSync(
    process.execPath,
    ["dist/invoice.js", '[{"unitCents":199,"quantity":3}]'],
    { encoding: "utf-8" }
  );
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout.trim(), "597");
  const invalid = spawnSync(process.execPath, ["dist/invoice.js", "invalid"], {
    encoding: "utf-8"
  });
  assert.equal(invalid.status, 1);
});
