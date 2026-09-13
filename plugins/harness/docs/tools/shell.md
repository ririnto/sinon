# Shell Tool Reference

This document owns the `shell` profile commands and target integration rules for repositories with shell scripts.
It is the tool-level companion to [the shell language rules](../languages/shell.md) and the shared rules in `docs/rules.md`.
The only native configuration source lives under `tooling/shell/`.

## Scope And Detection

Select the `shell` profile for a repository root that tracks `*.sh` files.
There is no manifest for this profile: the file list comes from git itself.
The profile adds no wrapper script, no task runner, and no test harness; the commands below are the whole gate.

## Toolchain

- ShellCheck: any currently maintained release; version 0.11 is current at authoring time.
- shfmt: any currently maintained release; version 3.14 is current at authoring time.
- Install through the platform package manager; do not install global tools to conceal a missing dependency on a target machine.

## Native Configuration Sources

| Source file | Destination | Write rule |
| --- | --- | --- |
| `tooling/shell/.editorconfig` | merge into existing `.editorconfig` | merge only missing sections and keys |

The `[*.sh]` tab-indent section matches shfmt's default style.
When the target already owns an `.editorconfig`, keep its shell section and resolve conflicts in favor of the target.

## Validation Commands

Run at the repository root, over git-tracked scripts only:

```sh
git ls-files -z -- '*.sh' | xargs -0 -r shellcheck -S warning --
git ls-files -z -- '*.sh' | xargs -0 -r shfmt -d --
```

`shfmt -w` applies approved formatting fixes in place.
The null-delimited pipeline keeps names with spaces or shell metacharacters safe.
The `--` option terminator prevents tracked paths from parsing as flags.
Missing ShellCheck or shfmt fails visibly; never substitute a weaker check.

## Dialect Selection

ShellCheck and shfmt infer the dialect from the shebang line.
A `#!/usr/bin/env sh` shebang checks as POSIX `sh`; a `bash` shebang checks as Bash and permits Bash-only syntax.
When a script's shebang and its syntax disagree, fix the shebang or the syntax at the root cause instead of adding per-file exemptions.
Per-file directives such as `# shellcheck shell=bash` are for generated or multi-dialect files only.

## CI Behavior

The GitHub catalog `ci/github/shell.yaml` and GitLab catalog `ci/gitlab/shell.gitlab-ci.yaml` install the two tools from the system package repository and run the same two commands.
The GitLab job declares `stage: validate`; add that stage to the target's pipeline stages when it does not exist.
No working-directory adjustment exists for this profile; the file list is repository-wide by design.

## Known Limitations

- Package-manager versions of ShellCheck and shfmt lag upstream releases; the checks remain valid, but new checks appear only after a runner image update.
- The Ubuntu and Debian archives carry old releases: Ubuntu 24.04 gives shfmt 3.8.0 and ShellCheck 0.9.0; Debian stable gives shfmt 3.8.0 and ShellCheck 0.10.0, versus upstream 3.14 and 0.11.
- `debian:stable-slim` pins the GitLab job's tool versions to the Debian release; rebuild freshness follows the image, and a target needing exact pins must install release archives instead.
