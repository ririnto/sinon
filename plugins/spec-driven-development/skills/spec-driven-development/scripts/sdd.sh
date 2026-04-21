#!/usr/bin/env sh
set -e
script_dir="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec uvx --from "${script_dir}" sdd "$@"
