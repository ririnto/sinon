import assert from "node:assert/strict";
import { test } from "node:test";

import { multiplyCents } from "./index.js";

await test("multiplies integer prices without floating-point currency arithmetic", () => {
  assert.equal(multiplyCents(199, 3), 597);
  assert.equal(multiplyCents(0, 3), 0);
});

await test("rejects invalid inputs and unsafe totals", () => {
  for (const [price, quantity] of [
    [1.5, 1],
    [1, -1],
    [Number.MAX_SAFE_INTEGER, 2]
  ]) {
    assert.throws(() => multiplyCents(price, quantity), RangeError);
  }
});
