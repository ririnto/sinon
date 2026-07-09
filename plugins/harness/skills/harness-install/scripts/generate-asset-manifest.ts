#!/usr/bin/env bun
// -*- coding: utf-8 -*-

import { generateManifestMain } from "./asset-manifest.js";

if (import.meta.main === null) {
  console.error("generate-asset-manifest.ts must run as a script");
  process.exit(1);
}

process.exit(generateManifestMain());
