#!/usr/bin/env sh
set -eu

if [ -f settings.gradle ] || [ -f settings.gradle.kts ] || [ -f build.gradle ] || [ -f build.gradle.kts ]; then
  printf '%s\n' gradle
  exit 0
fi
if [ -f pom.xml ]; then
  printf '%s\n' maven
  exit 0
fi
if [ -f uv.lock ] || { [ -f pyproject.toml ] && grep -Eq '^\[project\]|^\[tool\.uv\]' pyproject.toml; }; then
  printf '%s\n' uv
  exit 0
fi
if [ -f bun.lock ] || [ -f bun.lockb ] || [ -f package.json ]; then
  printf '%s\n' bun
  exit 0
fi
printf '%s\n' unknown
