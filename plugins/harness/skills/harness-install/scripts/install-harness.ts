#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { main } from "./install-harness/cli.js";
import { HarnessError } from "./install-harness/types.js";

if (process.execArgv.includes("--check")) {
  process.exit(0);
}

try {
  const exitCode = await main(Bun.argv.slice(2));
  process.exit(exitCode);
} catch (error) {
  if (error instanceof HarnessError) {
    console.error(error.message);
    process.exit(error.exitCode);
  }
  throw error;
}
