#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

root=${root:?}

# Smoke-check a native tool.
#
# @param tool_name Tool command name.
# @return Returns 0 after running or skipping the smoke check.
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
  printf 'error: %s --version failed\n' "$tool_name" >&2
  return 1
}
