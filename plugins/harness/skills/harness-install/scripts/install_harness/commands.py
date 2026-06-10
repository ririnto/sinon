from __future__ import annotations

from .errors import fail


def workflow_name_for_mode(mode: str) -> str:

    return {
        "gradle": "ktlint.yaml",
        "maven": "spotless.yaml",
        "uv": "ruff.yaml",
        "bun": "ultracite.yaml",
        "shell": "shellcheck.yaml",
    }[mode]


def validation_command_for_mode(mode: str) -> str:

    if mode == "gradle":
        return "./gradlew ktlintCheck"
    if mode == "maven":
        return (
            'root=$(pwd -P); files=$(git ls-files -- "*.java" | while IFS= read -r file; do '
            'case "$file" in *,*) echo "error: Java path contains comma and cannot be represented in spotlessFiles: $file" >&2; exit 1;; esac; '
            "printf '%s/%s\\n' \"$root\" \"$file\" | sed 's/[][\\.^$*+?{}()|]/\\&/g; s/^/^/; s/$/$/'; done | paste -sd, -); "
            'if [ -z "$files" ]; then ./mvnw validate; echo "spotless: no tracked Java files to check"; '
            'else ./mvnw validate -DspotlessFiles="$files"; fi'
        )
    if mode == "uv":
        return "uv run scripts/check.py"
    if mode == "bun":
        return "bun run check"
    if mode == "shell":
        return "sh scripts/check.sh"
    fail(
        f"[validation_command] unsupported mode (must be gradle|maven|uv|bun|shell): {mode}"
    )
