#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

fix_job_file=$(mktemp)
failures_file=$(mktemp)
trap 'rm -f "$fix_job_file" "$failures_file"' EXIT

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

# Fix Markdown files with markdownlint-cli2.
#
# @return Exits with 0 when Markdown fixes pass.
fix_markdown_files() {
  if markdownlint_bin=$(command -v markdownlint-cli2 2>&1); then
    "$markdownlint_bin" --fix
  else
    echo 'warning: markdownlint-cli2 not in PATH; skipping Markdown fixes' >&2
  fi
}

# Fix tracked shell scripts with shfmt.
#
# @param shell_file_list Null-delimited shell file list.
# @return Exits with 0 when shfmt succeeds.
fix_shell_files() {
  shell_file_list="$1"
  if ! shfmt_bin=$(command -v shfmt 2>&1); then
    echo 'error: shfmt is required for shell formatting. Install shfmt and rerun this fixer.' >&2
    return 1
  fi
  xargs -0 "$shfmt_bin" -w -- <"$shell_file_list"
  echo 'fixed tracked shell files'
}

# Run one fix by name.
#
# @param fix_name Fix function name.
# @param shell_file_list Null-delimited shell file list.
# @return Exits with the selected fix result.
run_fix_by_name() {
  fix_name="$1"
  shell_file_list="$2"
  case "$fix_name" in
  markdown) fix_markdown_files ;;
  shell) fix_shell_files "$shell_file_list" ;;
  *)
    printf 'error: unknown fix: %s\n' "$fix_name" >&2
    return 1
    ;;
  esac
}

# Run one fix in a background job.
#
# @param fix_name Fix function name.
# @param shell_file_list Null-delimited shell file list.
# @return Appends job metadata to the fix job file.
run_fix_job() {
  fix_name="$1"
  shell_file_list="$2"
  output_file=$(mktemp)
  (
    run_fix_by_name "$fix_name" "$shell_file_list" >"$output_file" 2>&1
  ) &
  printf '%s %s %s\n' "$!" "$fix_name" "$output_file" >>"$fix_job_file"
}

# Wait for background fixes and collect diagnostics.
#
# @return Writes failing fix names into the failures file.
wait_fix_jobs() {
  while IFS=' ' read -r pid fix_name output_file; do
    if wait "$pid"; then
      job_rc=0
    else
      job_rc=$?
    fi
    if [ -s "$output_file" ]; then
      cat "$output_file" >&2
    fi
    if [ "$job_rc" -ne 0 ]; then
      printf '%s\n' "$fix_name" >>"$failures_file"
    fi
    rm -f "$output_file"
  done <"$fix_job_file"
}

# Fix tracked Markdown and shell scripts in the repository.
#
# @return Exits with 0 on success, 1 on format or validation failure.
main() {
  script_dir=$(CDPATH='' cd "$(dirname "$0")" && pwd)
  shell_file_list=$(mktemp)
  trap 'rm -f "$fix_job_file" "$failures_file" "$shell_file_list"' EXIT
  write_tracked_shell_files "$shell_file_list"
  run_fix_job markdown "$shell_file_list"
  if [ -s "$shell_file_list" ]; then
    run_fix_job shell "$shell_file_list"
  else
    echo 'shfmt: no tracked shell files to fix'
  fi
  wait_fix_jobs
  if [ -s "$failures_file" ]; then
    exit 1
  fi
  echo 'remaining findings after fixes:'
  sh "$script_dir/check.sh"
}

main "$@"
