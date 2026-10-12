/**
 * Reserve reset-credit consumption for a separate direct human instruction.
 */
export const BLOCKED_METHODS = new Set([
  "account/rateLimitResetCredit/consume"
]);

/**
 * Route thread cleanup to the native CLI, whose commands carry the cleanup rules.
 */
export const NATIVE_CLI_METHODS = new Set([
  "thread/archive",
  "thread/delete",
  "thread/unarchive"
]);

/**
 * Keep usage reads with the Orchestrator.
 */
export const USAGE_METHODS = new Set([
  "account/rateLimits/read",
  "account/usage/read"
]);

/**
 * Return the refusal text for a method this server never sends, or null.
 */
export const refusal = (method) => {
  if (BLOCKED_METHODS.has(method)) {
    return `${method} requires a separate direct human instruction`;
  }
  if (NATIVE_CLI_METHODS.has(method)) {
    return `${method} runs through the native codex archive|unarchive|delete --remote unix:// command`;
  }
  return null;
};
