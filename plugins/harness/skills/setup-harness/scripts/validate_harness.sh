#!/bin/sh
# :description: Run the harness validator through uv-managed Python.
#     Exits 0 when validation passes, exits 1 on validation failure or missing uv.
# :param config_path: Repository-relative harness config path, overridden by HARNESS_CONFIG.
# :param root_path: Repository root directory, overridden by HARNESS_ROOT.
set -eu

config_path="${HARNESS_CONFIG:-docs/harness-engineering/harness-engineering.json}"
root_path="${HARNESS_ROOT:-.}"
script_dir="$(CDPATH= cd "$(dirname "$0")" && pwd)"
validator_path="${script_dir}/validate_harness.py"
has_config=false
has_root=false

for arg in "$@"; do
    case "${arg}" in
        --config) has_config=true ;;
        --config=*) has_config=true ;;
        --root) has_root=true ;;
        --root=*) has_root=true ;;
    esac
done

if [ "${has_config}" = false ]; then
    set -- --config "${config_path}" "$@"
fi
if [ "${has_root}" = false ]; then
    set -- --root "${root_path}" "$@"
fi

exec uv run "${validator_path}" "$@"
