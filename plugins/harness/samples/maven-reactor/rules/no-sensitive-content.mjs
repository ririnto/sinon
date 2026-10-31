const tokenPatterns = [
  /\b\d{6,}:[A-Za-z0-9_-]{30,}\b/u,
  /\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{40,})\b/u,
  /\b(?:AKIA|ASIA)[0-9A-Z]{16}\b/u,
  /\bAIza[0-9A-Za-z_-]{35}\b/u,
  /\bxox[baprs]-[A-Za-z0-9-]{10,}\b/u,
  /\bBearer\s+[A-Za-z0-9._~+/=-]{20,}/iu,
  /\bsk-[A-Za-z0-9_-]{20,}\b/u
];

const secretAssignmentPattern =
  /\b[A-Z0-9_]*(?:TOKEN|API[_-]?KEY|SECRET|PASSWORD|PRIVATE[_-]?KEY|ACCESS[_-]?KEY)[A-Z0-9_]*\s*[:=]\s*["']?(?<value>[A-Za-z0-9._~+/=-]{20,})/iu;

const placeholderPattern =
  /(?:example|sample|replace|placeholder|changeme|your[_-]?token|<[^>]+>)/iu;

const environmentPatterns = [
  /(?:^|[\s"'`(])\/Users\/(?!Shared\/|Guest\/)[^/\s"'`]+/u,
  /(?:^|[\s"'`(])\/home\/[^/\s"'`]+/u,
  /\b[A-Z]:\\Users\\[^\\\s"'`]+/iu
];

const hashPatterns = [
  /\/(?:commit|blob|tree)\/[0-9a-f]{7,64}\b/iu,
  /\b(?:git\s+)?(?:hash|commit(?:\s+(?:hash|id))?|revision|file\s+hash|sha-?1|sha-?256|sha-?512|md5)\s*[:=# ]+\s*[0-9a-f]{7,128}\b/iu,
  /\b[0-9a-f]{40}(?:[0-9a-f]{24})?\b/iu
];

/**
 * Report sensitive-content heuristics once per Markdownlint line without fixes or values.
 *
 * @type {import("markdownlint").Rule}
 */
export default {
  description:
    "Disallows credentials, local environment paths, and Git or file hashes",
  function(params, onError) {
    for (const [index, line] of params.lines.entries()) {
      const hasToken = tokenPatterns.some((pattern) => pattern.test(line));
      const secretAssignment =
        secretAssignmentPattern.exec(line)?.groups?.value;
      const hasSecretAssignment =
        secretAssignment !== undefined &&
        !placeholderPattern.test(secretAssignment);
      const hasEnvironmentPath = environmentPatterns.some((pattern) =>
        pattern.test(line)
      );
      const hasHash = hashPatterns.some((pattern) => pattern.test(line));
      if (hasToken || hasSecretAssignment || hasEnvironmentPath || hasHash) {
        onError({
          detail:
            "Remove credentials, local environment paths, and Git or file hashes",
          lineNumber: index + 1
        });
      }
    }
  },
  names: ["sensitive-content"],
  parser: "none",
  tags: ["security"]
};
