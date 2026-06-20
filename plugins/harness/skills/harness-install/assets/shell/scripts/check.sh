#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

check_job_file=$(mktemp)
failures_file=$(mktemp)
trap 'rm -f "$check_job_file" "$failures_file"' EXIT

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

# Check Markdown files with markdownlint-cli2.
#
# @return Exits with 0 when Markdown checks pass.
check_markdown_files() {
  if markdownlint_bin=$(command -v markdownlint-cli2 2>&1); then
    "$markdownlint_bin"
  else
    echo 'warning: markdownlint-cli2 not in PATH; skipping Markdown linting' >&2
  fi
}

# Check shell files with shellcheck.
#
# @param shell_file_list Null-delimited shell file list.
# @return Exits with 0 when shellcheck passes.
check_shellcheck_files() {
  shell_file_list="$1"
  xargs -0 shellcheck -S warning -- <"$shell_file_list" 2>&1
}

# Check shell files with shfmt.
#
# @param shell_file_list Null-delimited shell file list.
# @return Exits with 0 when shfmt reports no diff.
check_shfmt_files() {
  shell_file_list="$1"
  xargs -0 shfmt -d -- <"$shell_file_list" 2>&1
}

# Run one check by name.
#
# @param check_name Check function name.
# @param shell_file_list Null-delimited shell file list.
# @return Exits with the selected check result.
run_check_by_name() {
  check_name="$1"
  shell_file_list="$2"
  case "$check_name" in
  markdown) check_markdown_files ;;
  shellcheck) check_shellcheck_files "$shell_file_list" ;;
  shfmt) check_shfmt_files "$shell_file_list" ;;
  *)
    printf 'error: unknown check: %s\n' "$check_name" >&2
    return 1
    ;;
  esac
}

# Run one check in a background job.
#
# @param check_name Check function name.
# @param shell_file_list Null-delimited shell file list.
# @return Appends job metadata to the check job file.
run_check_job() {
  check_name="$1"
  shell_file_list="$2"
  output_file=$(mktemp)
  (
    run_check_by_name "$check_name" "$shell_file_list" >"$output_file" 2>&1
  ) &
  printf '%s %s %s\n' "$!" "$check_name" "$output_file" >>"$check_job_file"
}

# Wait for background checks and collect diagnostics.
#
# @return Writes failing check names into the failures file.
wait_check_jobs() {
  while IFS=' ' read -r pid check_name output_file; do
    if wait "$pid"; then
      job_rc=0
    else
      job_rc=$?
    fi
    if [ -s "$output_file" ]; then
      cat "$output_file" >&2
    fi
    if [ "$job_rc" -ne 0 ]; then
      printf '%s\n' "$check_name" >>"$failures_file"
    fi
    rm -f "$output_file"
  done <"$check_job_file"
}

# Validate Markdown and tracked shell scripts.
#
# @return Exits with 0 when all scripts pass lint and format checks, 1 on violations.
main() {
  shell_file_list=$(mktemp)
  trap 'rm -f "$check_job_file" "$failures_file" "$shell_file_list"' EXIT
  write_tracked_shell_files "$shell_file_list"
  run_check_job markdown "$shell_file_list"
  if [ -s "$shell_file_list" ]; then
    run_check_job shellcheck "$shell_file_list"
    run_check_job shfmt "$shell_file_list"
  else
    echo 'shellcheck and shfmt: no tracked shell files to check'
  fi
  wait_check_jobs
  if [ -s "$failures_file" ]; then
    exit 1
  fi
  echo 'shellcheck and shfmt: all scripts passed'
}

main "$@"
