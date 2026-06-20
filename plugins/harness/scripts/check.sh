#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/../../.." && pwd)}
check_job_file=$(mktemp)
current_warn_file=
trap 'rm -f "$check_job_file"' EXIT

# Increment the current job warning counter by one.
#
# @return Updates the warn counter file.
warn_count_increment() {
  if [ -n "$current_warn_file" ]; then
    printf '1\n' >>"$current_warn_file"
  fi
}

# List tracked shell scripts covered by repository checks.
#
# @return Writes one path per line.
list_shell_files() {
  git -C "$root" ls-files --full-name -- '*.sh' | while IFS= read -r path; do
    printf '%s/%s\n' "$root" "$path"
  done
}

# Check marketplace and plugin package metadata.
#
# Runs the package validator over JSON manifests, README inventories, skill frontmatter,
# and agent routing tables.
#
# @return Writes 0 when validation passes, 1 when diagnostics are found.
check_plugin_packages() {
  validator=$root/plugins/harness/scripts/plugin-self-check/package-checks.py
  if ! python_bin=$(command -v python3 2>&1); then
    echo 'warning: python3 not in PATH; skipping plugin package validation' >&2
    warn_count_increment
    echo '0'
    return
  fi
  package_output=$("$python_bin" "$validator" "$root" 2>&1) && package_rc=0 || package_rc=$?
  if [ "$package_rc" -ne 0 ]; then
    printf '%s\n' "$package_output" >&2
    echo '1'
    return
  fi
  printf '%s\n' "$package_output" >&2
  echo '0'
}

# Check shell files with shellcheck.
#
# Runs shellcheck on tracked shell scripts. Missing tools emit warnings.
#
# @return Accumulates error count.
check_shellcheck_files() {
  shell_file_list=$(mktemp)
  list_shell_files >"$shell_file_list"
  if [ ! -s "$shell_file_list" ]; then
    rm -f "$shell_file_list"
    echo '0'
    return
  fi
  if ! shellcheck_bin=$(command -v shellcheck 2>&1); then
    echo 'warning: shellcheck not in PATH; skipping shellcheck' >&2
    warn_count_increment
    shellcheck_bin=
  fi
  if [ -n "$shellcheck_bin" ]; then
    shellcheck_output=$(xargs "$shellcheck_bin" -x -P "$root/plugins/harness/scripts/plugin-self-check" <"$shell_file_list" 2>&1) && shellcheck_rc=0 || shellcheck_rc=$?
    rm -f "$shell_file_list"
    if [ "$shellcheck_rc" -ne 0 ]; then
      printf '%s\n' "$shellcheck_output" >&2
      echo '1'
      return
    fi
    echo '0'
    return
  fi
  rm -f "$shell_file_list"
  echo '0'
}

# Check shell files with shfmt.
#
# Runs shfmt on tracked shell scripts. Missing tools emit warnings.
#
# @return Accumulates error count.
check_shfmt_files() {
  shell_file_list=$(mktemp)
  list_shell_files >"$shell_file_list"
  if [ ! -s "$shell_file_list" ]; then
    rm -f "$shell_file_list"
    echo '0'
    return
  fi
  if ! shfmt_bin=$(command -v shfmt 2>&1); then
    echo 'warning: shfmt not in PATH; skipping shfmt' >&2
    warn_count_increment
    shfmt_bin=
  fi
  if [ -n "$shfmt_bin" ]; then
    shfmt_diff=$(xargs "$shfmt_bin" -d <"$shell_file_list" 2>&1) && shfmt_rc=0 || shfmt_rc=$?
    rm -f "$shell_file_list"
    if [ "$shfmt_rc" -ne 0 ]; then
      printf '%s\n' "$shfmt_diff" >&2
      echo '1'
      return
    elif [ -n "$shfmt_diff" ]; then
      printf '%s\n' "$shfmt_diff" >&2
      echo '1'
      return
    fi
    echo '0'
    return
  fi
  rm -f "$shell_file_list"
  echo '0'
}

# Check Markdown files with markdownlint-cli2.
#
# Runs markdownlint-cli2 on all Markdown files, collecting findings in
# structured diagnostic format and continuing on tool failure. Missing tool emits warning.
#
# @return Accumulates error count.
check_markdown_files() {
  error_count=0
  if ! markdownlint_bin=$(command -v markdownlint-cli2 2>&1); then
    echo 'warning: markdownlint-cli2 not in PATH; skipping markdown linting' >&2
    warn_count_increment
    echo '0'
    return
  fi
  markdown_file_list=$(mktemp)
  if ! git -C "$root" ls-files -z -- '*.md' >"$markdown_file_list"; then
    echo 'error: git ls-files failed while listing Markdown files' >&2
    rm -f "$markdown_file_list"
    echo '1'
    return
  fi
  if [ ! -s "$markdown_file_list" ]; then
    rm -f "$markdown_file_list"
    echo '0'
    return
  fi
  lint_output=$(cd "$root" && xargs -0 "$markdownlint_bin" <"$markdown_file_list" 2>&1) && lint_rc=0 || lint_rc=$?
  rm -f "$markdown_file_list"
  if [ "$lint_rc" -ne 0 ]; then
    printf '%s\n' "$lint_output" >&2
    error_count=1
  fi
  printf '%d\n' "$error_count"
}

# Check Python files with ruff check.
#
# Runs ruff check with Ruff's project discovery, collecting
# findings in structured diagnostic format and continuing on tool failure. Missing tools emit warnings.
#
# @return Accumulates error count.
check_python_lint() {
  if ! uv_bin=$(command -v uv 2>&1); then
    echo 'warning: uv not in PATH; skipping ruff check' >&2
    warn_count_increment
    echo '0'
    return
  fi
  check_output=$(cd "$root" && "$uv_bin" run --with 'ruff>=0.15.18,<0.16.0' ruff check . 2>&1) && check_rc=0 || check_rc=$?
  if [ "$check_rc" -ne 0 ]; then
    printf '%s\n' "$check_output" >&2
    echo '1'
    return
  fi
  echo '0'
}

# Check Python files with ruff format.
#
# Runs ruff format with Ruff's project discovery. Missing tools emit warnings.
#
# @return Accumulates error count.
check_python_format() {
  if ! uv_bin=$(command -v uv 2>&1); then
    echo 'warning: uv not in PATH; skipping ruff format' >&2
    warn_count_increment
    echo '0'
    return
  fi
  format_check_output=$(cd "$root" && "$uv_bin" run --with 'ruff>=0.15.18,<0.16.0' ruff format --check . 2>&1) && format_rc=0 || format_rc=$?
  if [ "$format_rc" -ne 0 ]; then
    printf '%s\n' "$format_check_output" >&2
    echo '1'
    return
  fi
  echo '0'
}

# Run one repository check in a background job.
#
# @param check_name Function name to run.
# @return Appends job metadata to the check job file.
run_check_by_name() {
  check_name=$1
  case "$check_name" in
  check_shellcheck_files) check_shellcheck_files ;;
  check_shfmt_files) check_shfmt_files ;;
  check_python_lint) check_python_lint ;;
  check_python_format) check_python_format ;;
  check_markdown_files) check_markdown_files ;;
  check_plugin_packages) check_plugin_packages ;;
  *)
    printf 'error: unknown check: %s\n' "$check_name" >&2
    return 1
    ;;
  esac
}

# Run one repository check in a background job.
#
# @param check_name Function name to run.
# @return Appends job metadata to the check job file.
run_check_job() {
  check_name=$1
  output_file=$(mktemp)
  result_file=$(mktemp)
  warn_file=$(mktemp)
  : >"$warn_file"
  (
    current_warn_file=$warn_file
    if check_errors=$(run_check_by_name "$check_name" 2>"$output_file"); then
      printf '%s\n' "$check_errors" >"$result_file"
    else
      printf '1\n' >"$result_file"
      exit 1
    fi
  ) &
  printf '%s %s %s %s\n' "$!" "$output_file" "$result_file" "$warn_file" >>"$check_job_file"
}

# Wait for background repository checks and collect diagnostics.
#
# @return Sets total_error_count and total_warn_count.
wait_check_jobs() {
  error_count=0
  warn_count=0
  while IFS=' ' read -r pid output_file result_file warn_file; do
    if wait "$pid"; then
      job_rc=0
    else
      job_rc=$?
    fi
    if [ -s "$output_file" ]; then
      cat "$output_file" >&2
    fi
    if [ -s "$result_file" ]; then
      job_errors=$(cat "$result_file")
    else
      job_errors=1
    fi
    if [ "$job_rc" -ne 0 ]; then
      job_errors=$((job_errors + 1))
    fi
    job_warnings=$(wc -l <"$warn_file" | tr -d ' ')
    error_count=$((error_count + job_errors))
    warn_count=$((warn_count + job_warnings))
    rm -f "$output_file" "$result_file" "$warn_file"
  done <"$check_job_file"
  total_error_count=$error_count
  total_warn_count=$warn_count
}

run_check_job check_shellcheck_files
run_check_job check_shfmt_files
run_check_job check_python_lint
run_check_job check_python_format
run_check_job check_markdown_files
run_check_job check_plugin_packages
wait_check_jobs
error_count=$total_error_count
warn_count=$total_warn_count
if [ "$error_count" -eq 0 ] && [ "$warn_count" -eq 0 ]; then
  echo 'Repository validation passed.'
else
  echo 'Repository validation reported diagnostics.'
fi

if [ "$error_count" -gt 0 ]; then
  exit 1
fi
exit 0
