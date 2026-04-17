#!/usr/bin/env sh
# -*- coding: utf-8 -*-

set -e

# :description: Validate a skill directory against the Agent Skills
#     specification using the official ``skills-ref`` reference library
#     (https://github.com/agentskills/agentskills/tree/main/skills-ref),
#     executed via ``uvx`` for zero-install invocation.
# :usage: validate_skill.sh <skill-directory>
# :param skill-directory: Path to the skill directory containing SKILL.md.
# :exit-code 0: Valid skill.
# :exit-code 1: Validation errors reported by skills-ref.
# :exit-code 2: Usage error (bad arguments or missing directory).
# :exit-code 3: ``uvx`` is not installed.

if [ $# -ne 1 ]; then
    cat >&2 <<'EOF'
Usage: validate_skill.sh <skill-directory>

Validates the skill against the Agent Skills specification by delegating
to the official `skills-ref` Python library, executed via `uvx`.
Equivalent to: uvx --from skills-ref agentskills validate <path>

Exit codes:
  0  valid
  1  validation errors reported
  2  usage error
  3  uvx not installed
EOF
    exit 2
fi

skill_dir="$1"

if [ ! -d "$skill_dir" ]; then
    echo "error: '$skill_dir' is not a directory" >&2
    exit 2
fi

if ! command -v uvx >/dev/null 2>&1; then
    cat >&2 <<'EOF'
error: uvx is not installed.

Install uv (which ships uvx) from https://docs.astral.sh/uv/:
    curl -LsSf https://astral.sh/uv/install.sh | sh
EOF
    exit 3
fi

skill_abs="$(cd "$skill_dir" && pwd)"
exec uvx --from skills-ref agentskills validate "$skill_abs"
