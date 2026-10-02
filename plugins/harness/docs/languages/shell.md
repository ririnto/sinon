---
metadata:
  reference:
    POSIX shell language:
      version: "2024"
      url: https://pubs.opengroup.org/onlinepubs/9799919799/utilities/V3_chap02.html
---

# Shell Language Rules

This document owns POSIX-shell-specific rules for the `shell` profile.
The [shared rules](../rules.md) apply alongside these shell-only invariants.
The [shell tool reference](../tools/shell.md) owns commands and configuration.

## Dialect And Portability

Default to POSIX `sh` and a `#!/usr/bin/env sh` shebang.
Use Bash features (`[[ ]]`, arrays, `local -n`, parameter expansion extras) only in scripts whose shebang declares `bash`, and only where POSIX cannot express the need.
Keep the dialect consistent inside one script.
Do not mix shebangs and syntax.

## Quoting And Expansion

Quote every expansion: `"$var"` and `"${var}"`, never bare `$var`.
Use `"$@"` for argument forwarding.
Prefer `${var:-default}` over separate existence checks.
Avoid `eval` outside a proven, quoted, single-purpose need, and never on data from an untrusted source.

## Errors And Control Flow

Start every script with `set -e`.
Check the exit status of commands whose failure changes the outcome.
Do not append `|| true` to hide failures.
Use `if command; then` over testing `$?` after the fact.
Brace syntax is POSIX `if/then/fi`, not JS-style braces.
Keep the native form.

## Files And Paths

Use `mktemp -d` for temporary directories and clean them with a trap.
Pass file lists null-delimited (`-z`, `-0`) whenever names cross a pipeline.
Use `--` option terminators when commands receive paths that begin with `-`.
Never parse `ls` output.

## Safety Boundaries

Treat every command that writes, removes, publishes, or reaches the network as a boundary crossing.
State its effect and target before running it.
Do not suppress diagnostics to `/dev/null`.
Print a visible message on failure instead.
Environment differences between macOS and Linux count as behavior changes.
Prefer the portable form when both exist.
