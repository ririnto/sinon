#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

script_dir=$(CDPATH='' cd "$(dirname "$0")" && pwd)

# Format all shell scripts under the current directory using shfmt.
#
# @return Exits with 0 on success after printing a summary.
format_sh_files() {
    count_file=$(mktemp)
    path_file=$(mktemp)
    echo 0 >"$count_file"
    trap 'rm -f "$count_file" "$path_file"' EXIT
    find . -type f -name '*.sh' -not -path './.git/*' | while IFS= read -r file; do
        before=$(cksum "$file")
        shfmt -i 4 -ci -w "$file"
        after=$(cksum "$file")
        if [ "$before" != "$after" ]; then
            current=$(cat "$count_file")
            echo "$((current + 1))" >"$count_file"
            printf '  %s\n' "${file#./}" >>"$path_file"
        fi
    done
    count=$(cat "$count_file")
    if [ "$count" -gt 0 ]; then
        printf 'formatted: %s\n' "$count"
        cat "$path_file"
    else
        printf '%s\n' 'no files formatted'
    fi
}

# Run the shell harness validation surface after formatting.
#
# @return Exits with the harness-check status.
check_after_format() {
    printf '%s\n' 'remaining findings after format:'
    sh "$script_dir/harness-check.sh"
}

format_sh_files
check_after_format
