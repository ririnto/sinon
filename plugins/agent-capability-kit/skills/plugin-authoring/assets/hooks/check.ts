#!/usr/bin/env bun
// -*- coding: utf-8 -*-

const payload = await Bun.stdin.text();
const secretFilePattern = /"(?<secretFile>\.env|credentials\.json|secrets)"/u;

if (secretFilePattern.test(payload)) {
  console.error("Blocked request that looks like a secret-file edit.");
  process.exit(2);
}
