#!/bin/sh
# :description: Run the harness validator by preferring local Python, falling back to uvx.
#     Exits 0 when validation passes, exits 1 on validation failure or missing interpreter.
# :param config_path: Repository-relative harness config path, overridden by HARNESS_CONFIG.
# :param root_path: Repository root directory, overridden by HARNESS_ROOT.
set -eu

config_path="${HARNESS_CONFIG:-docs/harness-engineering/harness-engineering.json}"
root_path="${HARNESS_ROOT:-.}"
script_dir="$(CDPATH= cd "$(dirname "$0")" && pwd)"
validator_path="${script_dir}/validate_harness.py"

if [ "$#" -eq 0 ]; then
    set -- --config "${config_path}" --root "${root_path}"
fi

python_cmd="$(command -v python3 || true)"
if [ -n "${python_cmd}" ]; then
    exec "${python_cmd}" "${validator_path}" "$@"
fi

python_cmd="$(command -v python || true)"
if [ -n "${python_cmd}" ]; then
    exec "${python_cmd}" "${validator_path}" "$@"
fi

exec uvx --offline --no-python-downloads python "${validator_path}" "$@"
