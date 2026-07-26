#!/usr/bin/env bun

import { main } from "./sdd/cli.js";

process.exitCode = main(Bun.argv.slice(2));
