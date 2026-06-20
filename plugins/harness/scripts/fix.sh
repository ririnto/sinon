#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/../../.." && pwd)}
changed_file=$(mktemp)
fix_job_file=$(mktemp)
current_changed_file=
trap 'rm -f "$changed_file" "$fix_job_file"' EXIT

# List tracked shell scripts covered by repository fixes.
#
# @return Writes one path per line.
list_shell_files() {
  git -C "$root" ls-files --full-name -- '*.sh' | while IFS= read -r path; do
    printf '%s/%s\n' "$root" "$path"
  done
}

# List git-tracked Markdown files covered by repository fixes.
#
# @return Writes one path per line.
list_markdown_files() {
  git -C "$root" ls-files --full-name -- '*.md' | while IFS= read -r path; do
    printf '%s/%s\n' "$root" "$path"
  done
}

# Count changed files after checksum comparison.
#
# @param before_file Sorted checksum file captured before formatting.
# @param after_file Sorted checksum file captured after formatting.
# @return Writes changed file count.
count_changed_checksums() {
  before_file=$1
  after_file=$2
  changed_count=$(comm -13 "$before_file" "$after_file" | wc -l | tr -d ' ')
  printf '%s\n' "$changed_count"
}

# Record changed paths from checksum comparison output.
#
# @param before_file Sorted checksum file captured before formatting.
# @param after_file Sorted checksum file captured after formatting.
# @return Writes changed paths into the current changed-file cache.
record_changed_checksum_paths() {
  before_file=$1
  after_file=$2
  changed_checksum_file=$(mktemp)
  comm -13 "$before_file" "$after_file" >"$changed_checksum_file"
  while IFS= read -r entry; do
    changed_path=${entry#* }
    changed_path=${changed_path#* }
    record_changed_file "$changed_path"
  done <"$changed_checksum_file"
  rm -f "$changed_checksum_file"
}

# Record one changed path if not already recorded.
#
# @param path Path of changed file.
# @return Writes path into the changed-file cache.
record_changed_file() {
  path=$1
  if [ -n "$current_changed_file" ]; then
    if ! grep -Fxq "$path" "$current_changed_file"; then
      printf '%s\n' "$path" >>"$current_changed_file"
    fi
  fi
}

# Fix shell files with shfmt.
#
# Writes fixed output in-place for all production shell scripts.
# Missing shfmt tool emits warning and skips.
#
# @return Writes changed file count and exits 0 or 1.
fix_shell_files() {
  if ! shfmt_bin=$(command -v shfmt 2>&1); then
    echo 'warning: shfmt not in PATH; skipping shfmt fixes' >&2
    echo '0'
    return 0
  fi
  shell_file_list=$(mktemp)
  before_file=$(mktemp)
  after_file=$(mktemp)
  list_shell_files >"$shell_file_list"
  if [ ! -s "$shell_file_list" ]; then
    rm -f "$shell_file_list" "$before_file" "$after_file"
    echo '0'
    return 0
  fi
  xargs cksum <"$shell_file_list" | sort >"$before_file"
  if ! xargs "$shfmt_bin" -w <"$shell_file_list" >&2; then
    rm -f "$shell_file_list" "$before_file" "$after_file"
    echo 'error: shfmt failed' >&2
    return 1
  fi
  xargs cksum <"$shell_file_list" | sort >"$after_file"
  changed_count=$(count_changed_checksums "$before_file" "$after_file")
  if [ "$changed_count" -gt 0 ]; then
    record_changed_checksum_paths "$before_file" "$after_file"
  fi
  rm -f "$shell_file_list" "$before_file" "$after_file"
  printf '%s\n' "$changed_count"
}

# Fix Markdown files with markdownlint-cli2 --fix.
#
# Writes fixed output in-place for all Markdown files.
# Missing markdownlint-cli2 tool emits warning and skips.
#
# @return Writes changed file count and exits 0 or 1.
fix_markdown_files() {
  if ! markdownlint_bin=$(command -v markdownlint-cli2 2>&1); then
    echo 'warning: markdownlint-cli2 not in PATH; skipping markdown fixes' >&2
    echo '0'
    return 0
  fi
  before_file=$(mktemp)
  after_file=$(mktemp)
  list_markdown_files | xargs cksum | sort >"$before_file"
  markdown_file_list=$(mktemp)
  if ! git -C "$root" ls-files -z -- '*.md' >"$markdown_file_list"; then
    echo 'error: git ls-files failed while listing Markdown files' >&2
    rm -f "$before_file" "$after_file" "$markdown_file_list"
    return 1
  fi
  if [ ! -s "$markdown_file_list" ]; then
    rm -f "$before_file" "$after_file" "$markdown_file_list"
    echo '0'
    return 0
  fi
  if (cd "$root" && xargs -0 "$markdownlint_bin" --fix <"$markdown_file_list" >&2); then
    list_markdown_files | xargs cksum | sort >"$after_file"
    changed_count=$(count_changed_checksums "$before_file" "$after_file")
    if [ "$changed_count" -gt 0 ]; then
      record_changed_checksum_paths "$before_file" "$after_file"
    fi
    rm -f "$before_file" "$after_file" "$markdown_file_list"
    printf '%s\n' "$changed_count"
    return 0
  else
    rm -f "$before_file" "$after_file" "$markdown_file_list"
    echo 'error: markdownlint-cli2 --fix failed' >&2
    return 1
  fi
}

# Fix Python files with ruff check --fix and ruff format.
#
# Writes fixed output in-place with Ruff's project discovery.
# Missing uv tool emits warning and skips.
#
# @return Writes aggregate count placeholder and exits 0 or 1.
fix_python_files() {
  if ! uv_bin=$(command -v uv 2>&1); then
    echo 'warning: uv not in PATH; skipping ruff fixes' >&2
    echo '0'
    return 0
  fi
  if ! (cd "$root" && "$uv_bin" run --with 'ruff>=0.15.18,<0.16.0' ruff check --fix . >&2); then
    echo 'error: ruff check --fix failed' >&2
    return 1
  fi
  if ! (cd "$root" && "$uv_bin" run --with 'ruff>=0.15.18,<0.16.0' ruff format . >&2); then
    echo 'error: ruff format failed' >&2
    return 1
  fi
  echo '0'
}

# Run one repository fix in a background job.
#
# @param fix_name Function name to run.
# @return Appends job metadata to the fix job file.
run_fix_by_name() {
  fix_name=$1
  case "$fix_name" in
  fix_shell_files) fix_shell_files ;;
  fix_python_files) fix_python_files ;;
  fix_markdown_files) fix_markdown_files ;;
  *)
    printf 'error: unknown fix: %s\n' "$fix_name" >&2
    return 1
    ;;
  esac
}

# Run one repository fix in a background job.
#
# @param fix_name Function name to run.
# @return Appends job metadata to the fix job file.
run_fix_job() {
  fix_name=$1
  output_file=$(mktemp)
  result_file=$(mktemp)
  job_changed_file=$(mktemp)
  : >"$job_changed_file"
  (
    current_changed_file=$job_changed_file
    if fixed_count=$(run_fix_by_name "$fix_name" 2>"$output_file"); then
      printf '%s\n' "$fixed_count" >"$result_file"
    else
      printf '0\n' >"$result_file"
      exit 1
    fi
  ) &
  printf '%s %s %s %s\n' "$!" "$output_file" "$result_file" "$job_changed_file" >>"$fix_job_file"
}

# Wait for background repository fixes and collect changed files.
#
# @return Sets total_fixed_count and total_error_count.
wait_fix_jobs() {
  fixed_count=0
  error_count=0
  while IFS=' ' read -r pid output_file result_file job_changed_file; do
    if wait "$pid"; then
      job_rc=0
    else
      job_rc=$?
    fi
    if [ -s "$output_file" ]; then
      cat "$output_file" >&2
    fi
    if [ -s "$result_file" ]; then
      job_fixed=$(cat "$result_file")
    else
      job_fixed=0
    fi
    fixed_count=$((fixed_count + job_fixed))
    if [ "$job_rc" -ne 0 ]; then
      error_count=$((error_count + 1))
    fi
    if [ -s "$job_changed_file" ]; then
      cat "$job_changed_file" >>"$changed_file"
    fi
    rm -f "$output_file" "$result_file" "$job_changed_file"
  done <"$fix_job_file"
  total_fixed_count=$fixed_count
  total_error_count=$error_count
}

run_fix_job fix_shell_files
run_fix_job fix_python_files
run_fix_job fix_markdown_files
wait_fix_jobs
fixed_count=$total_fixed_count
error_count=$total_error_count
if [ "$error_count" -gt 0 ]; then
  exit 1
fi

if [ "$fixed_count" -gt 0 ]; then
  echo 'fixed files:'
  if [ -s "$changed_file" ]; then
    sort -u "$changed_file" | while IFS= read -r changed_path; do
      printf '  %s\n' "$changed_path"
    done
  fi
else
  echo 'no files fixed'
fi
echo 'remaining findings after fixes:'
if sh "$root/plugins/harness/scripts/check.sh"; then
  check_status=0
else
  check_status=$?
fi
exit "$check_status"
