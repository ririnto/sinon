#!/bin/sh
set -eu

data_dir="${CLAUDE_PLUGIN_DATA:-.}"
state_dir="$data_dir/monitor-state"
state_file="$state_dir/example-monitor.txt"

mkdir -p "$state_dir"
date -u '+%Y-%m-%dT%H:%M:%SZ' > "$state_file"
printf '%s\n' "example-monitor wrote $state_file"
