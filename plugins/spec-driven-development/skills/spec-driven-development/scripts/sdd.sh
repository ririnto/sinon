#!/usr/bin/env sh
set -e
script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
skill_root="$(CDPATH= cd -- "${script_dir}/.." && pwd)"
if [ -z "${SDD_SKILL_ROOT:-}" ]; then
    SDD_SKILL_ROOT="${skill_root}"
fi
export SDD_SKILL_ROOT
exec uvx --from "${script_dir}" sdd "$@"
