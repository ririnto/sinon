#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

if [ -f settings.gradle ] || [ -f settings.gradle.kts ] || [ -f build.gradle ] || [ -f build.gradle.kts ]; then
    printf '%s\n' gradle
    exit 0
fi
if [ -f pom.xml ]; then
    printf '%s\n' maven
    exit 0
fi
if [ -f uv.lock ]; then
    printf '%s\n' uv
    exit 0
fi
if [ -f pyproject.toml ]; then
    if grep -Eq '^\[project\]|^\[tool\.uv\]' pyproject.toml; then
        printf '%s\n' uv
        exit 0
    fi
fi
if [ -f bun.lock ] || [ -f bun.lockb ] || [ -f package.json ]; then
    printf '%s\n' bun
    exit 0
fi
if [ -f Makefile ]; then
    printf '%s\n' shell
    exit 0
fi
shell_match=$(find . -maxdepth 1 -name '*.sh' -type f | head -n 1)
if [ -n "$shell_match" ]; then
    printf '%s\n' shell
    exit 0
fi
printf '%s\n' unknown
