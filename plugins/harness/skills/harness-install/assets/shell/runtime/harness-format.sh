#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Format all shell scripts under the current directory using shfmt.
#
# Discovers all .sh files matching the repository harness scope
# (excluding .git and other special directories) and applies shfmt
# with standard formatting options (4-space indent, compact if).
#
# @return Exits with 0 on success after printing a summary.
format_sh_files() {
    count_file=$(mktemp)
    echo 0 >"$count_file"
    trap 'rm -f "$count_file"' EXIT
    find . -type f -name '*.sh' -not -path './.git/*' | while IFS= read -r file; do
        shfmt -i 4 -ci -w "$file"
        current=$(cat "$count_file")
        echo "$((current + 1))" >"$count_file"
    done
    count=$(cat "$count_file")
    echo "Formatted $count file(s)."
}

format_sh_files
