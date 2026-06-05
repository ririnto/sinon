#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Write Git-tracked shell scripts.
#
# @param shell_file_list Destination file for null-delimited paths.
# @return Writes tracked shell paths to shell_file_list.
write_tracked_shell_files() {
    shell_file_list="$1"
    if ! git ls-files -z -- '*.sh' >"$shell_file_list"; then
        echo 'error: git ls-files failed while listing shell files' >&2
        return 1
    fi
}

# Format tracked shell scripts in the repository using shfmt.
#
# @return Exits with 0 on success, 1 on format or validation failure.
main() {
    script_dir=$(CDPATH='' cd "$(dirname "$0")" && pwd)
    if ! shfmt_bin=$(command -v shfmt 2>&1); then
        echo 'warning: shfmt not in PATH; skipping shell formatting' >&2
        exit 0
    fi
    shell_file_list=$(mktemp)
    trap 'rm -f "$shell_file_list"' EXIT
    write_tracked_shell_files "$shell_file_list"
    if [ ! -s "$shell_file_list" ]; then
        echo 'shfmt: no tracked shell files to format'
    else
        xargs -0 "$shfmt_bin" -i 4 -ci -w -- <"$shell_file_list"
        echo 'formatted tracked shell files'
    fi
    echo 'remaining findings after format:'
    sh "$script_dir/check.sh"
}

main "$@"
