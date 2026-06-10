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

# Validate Markdown and tracked shell scripts.
#
# @return Exits with 0 when all scripts pass lint and format checks, 1 on violations.
main() {
    bunx markdownlint-cli2
    failures_file=$(mktemp)
    shell_file_list=$(mktemp)
    trap 'rm -f "$failures_file" "$shell_file_list"' EXIT
    write_tracked_shell_files "$shell_file_list"
    if [ ! -s "$shell_file_list" ]; then
        echo 'shellcheck: no tracked shell files to check'
        return 0
    fi
    if ! xargs -0 shellcheck -S warning -- <"$shell_file_list" 2>&1; then
        echo 'shellcheck' >"$failures_file"
    fi
    if ! xargs -0 shfmt -d -i 4 -ci -- <"$shell_file_list" 2>&1; then
        echo 'shfmt' >"$failures_file"
    fi
    if [ -s "$failures_file" ]; then
        exit 1
    fi
    echo 'shellcheck and shfmt: all scripts passed'
}

main "$@"
