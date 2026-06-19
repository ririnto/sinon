#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${root:?}

# Smoke-check native linters.
# Gracefully skips if tool is unavailable.
smoke_test_tool() {
  tool_name=$1
  if ! tool_path=$(command -v "$tool_name" 2>&1); then
    printf 'note: %s not in PATH; skipping smoke test\n' "$tool_name" >&2
    return 0
  fi
  if [ -z "$tool_path" ]; then
    printf 'note: %s not in PATH; skipping smoke test\n' "$tool_name" >&2
    return 0
  fi
  if "$tool_name" --version; then
    printf '[smoke test] %s OK\n' "$tool_name" >&2
    return 0
  fi
  return 0
}

# Enforce first-party source size and long Markdown placement.
#
# @param scope_path Git pathspec to inspect.
# @exit Exits with status 1 when source modules exceed 250 pure LOC or long Markdown leaves references.
assert_source_size_policy() {
  scope_path=$1
  matches_file=$(mktemp)
  git -C "$root" ls-files -- "$scope_path" | while IFS= read -r file; do
    if [ ! -f "$root/$file" ]; then
      continue
    fi
    case "$file" in
    */node_modules/*) continue ;;
    esac
    case "$file" in
    *.py | *.sh | *.mjs | *.ts | *.kt | *.kts)
      pure_loc=$(awk '!/^[[:space:]]*$/ && !/^[[:space:]]*(#|\/\/|\/\*\*?|\*|\*\/)/ { c++ } END { print c + 0 }' "$root/$file")
      if [ "$pure_loc" -gt 250 ]; then
        printf '%s %s\n' "$pure_loc" "$file" >>"$matches_file"
      fi
      ;;
    *.md)
      markdown_loc=$(awk '!/^[[:space:]]*$/ { c++ } END { print c + 0 }' "$root/$file")
      if [ "$markdown_loc" -gt 250 ]; then
        case "$file" in
        skills/harness-install/assets/common/docs/references/*) ;;
        *) printf '%s %s\n' "$markdown_loc" "$file" >>"$matches_file" ;;
        esac
      fi
      ;;
    esac
  done
  if [ -s "$matches_file" ]; then
    printf '[assert_source_size_policy] oversized source file or misplaced long Markdown under %s:\n' "$scope_path" >&2
    cat "$matches_file" >&2
    rm -f "$matches_file"
    exit 1
  fi
  rm -f "$matches_file"
}
