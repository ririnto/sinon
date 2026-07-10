import type { CiHost, Mode } from "./types.js";
import { fail } from "./types.js";

export const workflowNameForMode = (mode: Mode): string => {
  switch (mode) {
    case "gradle": {
      return "ktlint.yaml";
    }
    case "maven": {
      return "spotless.yaml";
    }
    case "uv": {
      return "ruff.yaml";
    }
    case "bun": {
      return "ultracite.yaml";
    }
    case "shell": {
      return "shellcheck.yaml";
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${mode}`
      );
    }
  }
};

export const workflowAssetNameForCiHost = (ciHost: CiHost): string => {
  switch (ciHost) {
    case "github": {
      return "WORKFLOW.github.md";
    }
    case "gitlab": {
      return "WORKFLOW.gitlab.md";
    }
    case "both": {
      return "WORKFLOW.md";
    }
    case "none": {
      return "WORKFLOW.none.md";
    }
    default: {
      return fail(
        `unsupported ci host (must be github|gitlab|both|none): ${ciHost}`
      );
    }
  }
};

/**
 * Shared guard and `spotlessFiles` builder for Maven check and fix commands.
 *
 * @returns Shell fragments that reject escaped or comma-bearing tracked Java paths, then build an escaped, repo-root-anchored, comma-joined `$files` value.
 */
const mavenSpotlessFilesFragments = (): readonly string[] => [
  'root=$(pwd -P); if git ls-files -- "*.java" | grep -q \'^"\'; then',
  'unsafe_file=$(git ls-files -- "*.java" | grep \'^"\');',
  'echo "error: escaped Java path for spotlessFiles: $unsafe_file" >&2;',
  "exit 1; fi;",
  "if git ls-files -- \"*.java\" | grep -q ','; then",
  "comma_file=$(git ls-files -- \"*.java\" | grep ',');",
  'echo "error: comma Java path for spotlessFiles: $comma_file" >&2;',
  "exit 1; fi;",
  'files=$(git ls-files -- "*.java" | while IFS= read -r file; do',
  'printf \'%s/%s\\n\' "$root" "$file" |',
  "sed 's/[][\\\\.^$*+?{}()|]/\\\\&/g; s/^/^/; s/$/$/'; done | paste -sd, -);"
];

export const validationCommandForMode = (mode: Mode): string => {
  switch (mode) {
    case "gradle": {
      return "./gradlew ktlintCheck";
    }
    case "maven": {
      return [
        ...mavenSpotlessFilesFragments(),
        'if [ -z "$files" ]; then ./mvnw validate;',
        'echo "spotless: no tracked Java files to check";',
        'else ./mvnw validate -DspotlessFiles="$files"; fi'
      ].join(" ");
    }
    case "uv": {
      return "uv run scripts/check.py";
    }
    case "bun": {
      return "bun run check";
    }
    case "shell": {
      return "sh scripts/check.sh";
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${mode}`
      );
    }
  }
};

/**
 * Return the canonical auto-fix command for one stack mode.
 *
 * @param mode Selected stack mode.
 * @returns Shell command that applies safe lint/format fixes in place.
 */
export const fixCommandForMode = (mode: Mode): string => {
  switch (mode) {
    case "gradle": {
      return "./gradlew ktlintFormat";
    }
    case "maven": {
      return [
        ...mavenSpotlessFilesFragments(),
        'if [ -z "$files" ]; then ./mvnw exec:exec@format-markdown spotless:apply;',
        'else ./mvnw exec:exec@format-markdown spotless:apply -DspotlessFiles="$files"; fi'
      ].join(" ");
    }
    case "uv": {
      return "uv run scripts/fix.py";
    }
    case "bun": {
      return "bun run fix";
    }
    case "shell": {
      return "sh scripts/fix.sh";
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${mode}`
      );
    }
  }
};

/**
 * Return the final pre-push gate command for one stack mode.
 *
 * @param mode Selected stack mode.
 * @returns Shell command that runs the full validation gate before push.
 */
export const prePushCommandForMode = (mode: Mode): string => {
  switch (mode) {
    case "gradle": {
      return "./gradlew check";
    }
    case "maven": {
      return "./mvnw verify";
    }
    case "uv": {
      return "uv run scripts/check.py";
    }
    case "bun": {
      return "bun run check && bun test --pass-with-no-tests";
    }
    case "shell": {
      return "sh scripts/check.sh";
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${mode}`
      );
    }
  }
};

export const modeFromValue = (value: string): Mode => {
  switch (value) {
    case "gradle":
    case "maven":
    case "uv":
    case "bun":
    case "shell": {
      return value;
    }
    default: {
      return fail(
        `unsupported mode (must be gradle|maven|uv|bun|shell): ${value}`,
        2
      );
    }
  }
};

export const ciHostFromValue = (value: string): CiHost => {
  switch (value) {
    case "github":
    case "gitlab":
    case "both":
    case "none": {
      return value;
    }
    default: {
      return fail(
        `unsupported ci host (must be github|gitlab|both|none): ${value}`,
        2
      );
    }
  }
};
