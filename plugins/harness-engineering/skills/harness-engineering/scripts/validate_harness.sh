#!/bin/sh
set -eu

config_path="${HARNESS_CONFIG:-docs/harness-engineering/harness-engineering.json}"
root_path="${HARNESS_ROOT:-.}"

if [ "$#" -gt 0 ]; then
    exec uvx --offline --no-python-downloads python scripts/harness/validate_harness.py "$@"
fi

exec uvx --offline --no-python-downloads python scripts/harness/validate_harness.py --config "${config_path}" --root "${root_path}"
