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

# Fix tracked shell scripts in the repository using shfmt.
#
# @return Exits with 0 on success, 1 on format or validation failure.
main() {
  script_dir=$(CDPATH='' cd "$(dirname "$0")" && pwd)
  if markdownlint_bin=$(command -v markdownlint-cli2 2>&1); then
    "$markdownlint_bin" --fix
  else
    echo 'warning: markdownlint-cli2 not in PATH; skipping Markdown fixes' >&2
  fi
  if ! shfmt_bin=$(command -v shfmt); then
    echo 'error: shfmt is required for shell formatting. Install shfmt and rerun this fixer.' >&2
    exit 1
  fi
  shell_file_list=$(mktemp)
  trap 'rm -f "$shell_file_list"' EXIT
  write_tracked_shell_files "$shell_file_list"
  if [ ! -s "$shell_file_list" ]; then
    echo 'shfmt: no tracked shell files to fix'
  else
    xargs -0 "$shfmt_bin" -w -- <"$shell_file_list"
    echo 'fixed tracked shell files'
  fi
  echo 'remaining findings after fixes:'
  sh "$script_dir/check.sh"
}

main "$@"
