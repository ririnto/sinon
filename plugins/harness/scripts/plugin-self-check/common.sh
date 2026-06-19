#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${root:?}

# Return a path relative to the plugin root.
#
# @param path File or directory path to normalize.
# @return Prints the plugin-root-relative path.
relative_to_root() {
  path=$1
  case "$path" in
  "$root"/*) printf '%s\n' "${path#"$root"/}" ;;
  *) printf '%s\n' "$path" ;;
  esac
}

# Require a regular file in the plugin package.
#
# @param path File path to check.
# @exit Exits with status 1 when file is missing.
require_file() {
  path=$1
  if [ -f "$path" ]; then
    return 0
  fi
  relative_path=$(relative_to_root "$path")
  if ! tracked_file=$(git -C "$root" ls-files --error-unmatch -- "$relative_path" 2>&1); then
    printf '%s\n' "[require_file] missing required file: $path" >&2
    printf '%s\n' "  hint: ensure the file is committed and at the expected path" >&2
    exit 1
  fi
  if [ -z "$tracked_file" ]; then
    printf '%s\n' "[require_file] missing required file: $path" >&2
    printf '%s\n' "  hint: ensure the file is committed and at the expected path" >&2
    exit 1
  fi
}

# Require a file to be executable.
#
# @param path File path to check.
# @exit Exits with status 1 when file is not executable.
require_executable() {
  path=$1
  require_file "$path"
  if [ -x "$path" ]; then
    return 0
  fi
  printf '%s\n' "[require_executable] file is not executable: $path" >&2
  exit 1
}

# Require a directory in the plugin package.
#
# @param path Directory path to check.
# @exit Exits with status 1 when directory is missing.
require_dir() {
  path=$1
  relative_path=$(relative_to_root "$path")
  tracked_entries=$(git -C "$root" ls-files -- "$relative_path")
  if [ -z "$tracked_entries" ]; then
    printf '%s\n' "[require_dir] missing required directory: $path" >&2
    printf '%s\n' "  hint: ensure the directory is created and committed" >&2
    exit 1
  fi
}

# Require a file to contain a fixed string.
#
# @param path File path to search.
# @param text Fixed string to require.
# @exit Exits with status 1 when text is not found.
require_text() {
  path=$1
  text=$2
  if ! grep -Fq -- "$text" "$path"; then
    printf '%s\n' "[require_text] missing required text in $path: $text" >&2
    exit 1
  fi
}

# Enforce installer script style hardening contracts.
#
# @param installer_script Path to Python installer script or package directory.
# @param bun_plugin_script Path to Bun oxlint JS plugin script.
# @exit Exits with status 1 when style contracts are violated.
assert_code_style_contracts() {
  installer_script=$1
  bun_plugin_script=$2
  require_file "$bun_plugin_script"
  if [ -f "$installer_script" ]; then
    python3 -m py_compile "$installer_script"
    return 0
  fi
  relative_path=$(relative_to_root "$installer_script")
  git -C "$root" ls-files -- "$relative_path" | while IFS= read -r file; do
    case "$file" in
    *.py) python3 -m py_compile "$root/$file" ;;
    esac
  done
}
