import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const dataDir = process.env["CLAUDE_PLUGIN_DATA"] ?? ".";
const stateDir = path.join(dataDir, "monitor-state");
const stateFile = path.join(stateDir, "example-monitor.txt");
const INTERVAL_MILLISECONDS = 30_000;

const reportStatus = (): void => {
  mkdirSync(stateDir, { recursive: true });
  writeFileSync(stateFile, `${new Date().toISOString()}\n`, "utf-8");
  console.log(`example-monitor wrote ${stateFile}`);
};

reportStatus();
setInterval(reportStatus, INTERVAL_MILLISECONDS);
