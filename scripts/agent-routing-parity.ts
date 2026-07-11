import { normalizeDescription } from "./agent-routing-contract.js";
import type { ClaudeAgent, CodexAgent } from "./agent-routing-contract.js";

export const validateCounterpartParity = (
  codexPath: string,
  claude: ClaudeAgent | undefined,
  codex: CodexAgent | undefined,
  errors: string[]
): void => {
  if (claude === undefined || codex === undefined) {
    return;
  }
  const claudeDescription = claude.frontmatter["description"];
  const codexDescription = codex.toml["description"];
  if (
    typeof claudeDescription !== "string" ||
    typeof codexDescription !== "string" ||
    normalizeDescription(claudeDescription) !==
      normalizeDescription(codexDescription)
  ) {
    errors.push(`${codexPath}: counterpart description drift`);
  }
  if (claude.body !== codex.body) {
    errors.push(`${codexPath}: counterpart developer instructions drift`);
  }
};
