#!/usr/bin/env sh
# -*- coding: utf-8 -*-

set -e

# :description: Scaffold a new Agent Skill directory from the bundled template.
# :usage: new_skill.sh <skill-name> [target-directory]
# :param skill-name: Required. Lowercase alphanumeric and hyphens, 1-64 chars,
#     no leading/trailing/consecutive hyphens. Becomes both the directory
#     basename and the ``name`` frontmatter field.
# :param target-directory: Optional. Parent directory in which the new skill
#     directory is created. Defaults to the current working directory.
# :exit-code 0: Scaffold created successfully.
# :exit-code 1: Invalid skill name.
# :exit-code 2: Target directory does not exist or is not writable.
# :exit-code 3: A directory with the given name already exists at the target.
# :exit-code 4: The bundled template file is missing.

# :description: Absolute path to the directory containing this script. Used to
#     locate the bundled template under ``../assets/``.
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
readonly SCRIPT_DIR

# :description: Absolute path to the SKILL.md template bundled with this skill.
TEMPLATE_PATH="${SCRIPT_DIR}/../assets/SKILL.template.md"
readonly TEMPLATE_PATH

# :description: Regex matching a valid Agent Skill ``name``: 1-64 chars of
#     lowercase letters, digits, and single hyphens, with no leading, trailing,
#     or consecutive hyphens.
NAME_REGEX='^[a-z0-9]+(-[a-z0-9]+)*$'
readonly NAME_REGEX

# :description: Maximum allowed length of the ``name`` field, per spec.
NAME_MAX_LEN=64
readonly NAME_MAX_LEN

# :description: Print a usage message to stderr and exit with the given code.
# :param code: Integer exit status to return.
usage() {
    code="${1:-1}"
    cat >&2 <<'EOF'
Usage: new_skill.sh <skill-name> [target-directory]

Scaffolds a new Agent Skill directory containing SKILL.md, scripts/,
references/, and assets/ subdirectories. The skill name must be
1-64 lowercase alphanumeric characters with optional single hyphens.

Examples:
  new_skill.sh pdf-processing
  new_skill.sh code-review ./skills
EOF
    exit "$code"
}

# :description: Validate that the given string is a spec-compliant skill name.
#     Prints an error and exits 1 on failure.
# :param candidate: Name string to validate.
validate_name() {
    candidate="$1"
    if [ -z "$candidate" ]; then
        echo "error: skill name is empty" >&2
        exit 1
    fi
    candidate_len=${#candidate}
    if [ "$candidate_len" -gt "$NAME_MAX_LEN" ]; then
        echo "error: skill name '$candidate' exceeds $NAME_MAX_LEN characters" >&2
        exit 1
    fi
    if ! printf '%s' "$candidate" | grep -Eq "$NAME_REGEX"; then
        echo "error: skill name '$candidate' is invalid" >&2
        echo "       must match: $NAME_REGEX" >&2
        echo "       (lowercase a-z, 0-9, single hyphens, no leading/trailing/consecutive hyphens)" >&2
        exit 1
    fi
}

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
    usage 1
fi

skill_name="$1"
target_dir="${2:-.}"

validate_name "$skill_name"

if [ ! -d "$target_dir" ]; then
    echo "error: target directory '$target_dir' does not exist" >&2
    exit 2
fi
if [ ! -w "$target_dir" ]; then
    echo "error: target directory '$target_dir' is not writable" >&2
    exit 2
fi

if [ ! -f "$TEMPLATE_PATH" ]; then
    echo "error: template file not found at '$TEMPLATE_PATH'" >&2
    exit 4
fi

skill_dir="${target_dir%/}/$skill_name"
if [ -e "$skill_dir" ]; then
    echo "error: '$skill_dir' already exists" >&2
    exit 3
fi

mkdir -p "$skill_dir/scripts" "$skill_dir/references" "$skill_dir/assets"

sed "s/__SKILL_NAME__/$skill_name/g" "$TEMPLATE_PATH" > "$skill_dir/SKILL.md"

touch "$skill_dir/scripts/.gitkeep" "$skill_dir/references/.gitkeep" "$skill_dir/assets/.gitkeep"

echo "created: $skill_dir"
echo "next steps:"
echo "  1. edit $skill_dir/SKILL.md (at minimum, fill in the description field)"
echo "  2. remove scripts/, references/, or assets/ if unused"
echo "  3. run: sh $SCRIPT_DIR/validate_skill.sh $skill_dir"
