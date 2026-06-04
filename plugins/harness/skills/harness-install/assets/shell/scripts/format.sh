#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Format all shell scripts in the repository using shfmt.
#
# @return Exits with 0 on success, 1 on format or validation failure.
main() {
    script_dir=$(CDPATH='' cd "$(dirname "$0")" && pwd)
    if ! shfmt_bin=$(command -v shfmt 2>&1); then
        echo 'warning: shfmt not in PATH; skipping shell formatting' >&2
        exit 0
    fi
    changed_count=0
    shell_file_list=$(mktemp)
    trap 'rm -f "$shell_file_list"' EXIT
    find . \( -path './.git' -o -path './.claude/worktrees' \) -prune -o -type f -name '*.sh' -print >"$shell_file_list"
    while IFS= read -r file; do
        before=$(cksum "$file")
        if ! "$shfmt_bin" -i 4 -ci -w "$file"; then
            printf 'error: shfmt failed on %s\n' "$file" >&2
            exit 1
        fi
        after=$(cksum "$file")
        if [ "$before" != "$after" ]; then
            printf '  %s\n' "$file"
            changed_count=$((changed_count + 1))
        fi
    done <"$shell_file_list"
    if [ "$changed_count" -gt 0 ]; then
        printf 'formatted: %s\n' "$changed_count"
    else
        echo 'no files formatted'
    fi
    echo 'remaining findings after format:'
    sh "$script_dir/check.sh"
}

main "$@"
