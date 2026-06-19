#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${CLAUDE_PLUGIN_ROOT:-$(CDPATH='' cd "$(dirname "$0")/../../.." && pwd)}
changed_file=$(mktemp)
trap 'rm -f "$changed_file"' EXIT

# List tracked shell scripts covered by repository fixes.
#
# @return Writes one path per line.
list_shell_files() {
  git -C "$root" ls-files --full-name -- '*.sh' | while IFS= read -r path; do
    printf '%s/%s\n' "$root" "$path"
  done
}

# List production Python scripts covered by repository fixes.
#
# @return Writes one path per line.
list_python_files() {
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install-harness.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/__init__.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/advisory.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/cli.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/commands.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/contracts.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/errors.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/hooks.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/installer.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/models.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/operations.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/paths.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/planning.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/scripts/install_harness/preview.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/assets/uv/scripts/check.py"
  printf '%s\n' "$root/plugins/harness/skills/harness-install/assets/uv/scripts/fix.py"
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

# Record one changed path if not already recorded.
#
# @param path Path of changed file.
# @return Writes path into the changed-file cache.
record_changed_file() {
  path=$1
  if ! grep -Fxq "$path" "$changed_file"; then
    printf '%s\n' "$path" >>"$changed_file"
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
  fixed_count=0
  shell_fix_failed=0
  shell_file_list=$(mktemp)
  list_shell_files >"$shell_file_list"
  while IFS= read -r path; do
    if [ -f "$path" ]; then
      before_checksum=$(cksum "$path")
      if "$shfmt_bin" -w "$path" 2>&1; then
        after_checksum=$(cksum "$path")
        if [ "$before_checksum" != "$after_checksum" ]; then
          fixed_count=$((fixed_count + 1))
          record_changed_file "$path"
        fi
      else
        shell_fix_failed=1
        printf 'error: shfmt failed on %s\n' "$path" >&2
        break
      fi
    fi
  done <"$shell_file_list"
  rm -f "$shell_file_list"
  if [ "$shell_fix_failed" -ne 0 ]; then
    return 1
  fi
  printf '%d\n' "$fixed_count"
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
      changed_markdown_file=$(mktemp)
      comm -13 "$before_file" "$after_file" >"$changed_markdown_file"
      while IFS= read -r entry; do
        changed_path=${entry#* }
        changed_path=${changed_path#* }
        record_changed_file "$changed_path"
      done <"$changed_markdown_file"
      rm -f "$changed_markdown_file"
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

# Fix Python files with ruff format.
#
# Writes fixed output in-place for all production Python scripts.
# Missing uv tool emits warning and skips.
#
# @return Writes changed file count and exits 0 or 1.
fix_python_files() {
  if ! uv_bin=$(command -v uv 2>&1); then
    echo 'warning: uv not in PATH; skipping ruff fixes' >&2
    echo '0'
    return 0
  fi
  fixed_count=0
  python_fix_failed=0
  python_file_list=$(mktemp)
  list_python_files >"$python_file_list"
  while IFS= read -r path; do
    if [ -f "$path" ]; then
      before_checksum=$(cksum "$path")
      if "$uv_bin" run --with ruff==0.15.16 ruff format "$path" >&2; then
        after_checksum=$(cksum "$path")
        if [ "$before_checksum" != "$after_checksum" ]; then
          fixed_count=$((fixed_count + 1))
          record_changed_file "$path"
        fi
      else
        python_fix_failed=1
        printf 'error: ruff format failed on %s\n' "$path" >&2
        break
      fi
    fi
  done <"$python_file_list"
  rm -f "$python_file_list"
  if [ "$python_fix_failed" -ne 0 ]; then
    return 1
  fi
  printf '%d\n' "$fixed_count"
}

if ! shell_fixed=$(fix_shell_files); then
  exit 1
fi
if ! python_fixed=$(fix_python_files); then
  exit 1
fi
if ! markdown_fixed=$(fix_markdown_files); then
  exit 1
fi

if [ "$shell_fixed" -gt 0 ] || [ "$python_fixed" -gt 0 ] || [ "$markdown_fixed" -gt 0 ]; then
  echo 'fixed files:'
  if [ -s "$changed_file" ]; then
    sort "$changed_file" | while IFS= read -r changed_path; do
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
