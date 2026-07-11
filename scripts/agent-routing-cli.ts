import type { AgentRoutingResult } from "./agent-routing.js";

export const reportAgentRoutingResult = (
  result: AgentRoutingResult
): number => {
  for (const warning of result.warnings) {
    console.warn(`[agent routing warning] ${warning}`);
  }
  if (result.errors.length > 0) {
    console.error(
      `Agent routing validation failed:\n${result.errors.join("\n")}`
    );
    return 1;
  }
  console.error(
    `[agent routing] OK (${result.warnings.length} compatibility warning)`
  );
  return 0;
};
