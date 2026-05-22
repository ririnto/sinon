# Shell Harness Adapter

POSIX shell adapter for the repository harness. Used when the target project does not have a Gradle / Maven / uv / bun stack and runs primarily as a collection of shell scripts or a Makefile.

## What it validates

The shell adapter implements a minimum-viable subset of the harness manifest, sufficient for documentation-driven and shell-script projects:

- `requireFilesExist` — every `parameters.paths` entry exists as a regular file.
- `requireDirectoriesExist` — every `parameters.paths` entry exists as a directory.
- `requireKeepfileInEmptyDirectories` — every `parameters.directories` entry contains a `.gitkeep` or real file.
- `requireHookShebang` — each pre-commit / pre-push hook begins with the expected shebang.
- `requireHookExecutable` — each hook has the executable bit set.
- `forbidScaffoldLeaks` — no leak pattern (unresolved template tokens, deferred-work markers, scaffold prompt text, or example identifiers) appears in active assets. The exact patterns are read from the manifest at runtime.
- `forbidUncheckedTasksUnder` — no completed plan retains unchecked `- [ ]` task lines.

The richer set of add-ons (manifest-driven Kotlin / Java / Python / TypeScript checks) live in the matching language-specific adapters and is out of scope for the shell adapter.

## Runtime requirements

- POSIX `sh`
- `python3` 3.8 or newer, used to parse `docs/harness/manifest.json` (POSIX shell has no JSON parser)
- `find`, `grep`, `sed`

## How to run

```sh
sh docs/harness/shell/harness-validate.sh
```

Exit code is `0` when zero `ERROR` findings are emitted; `1` otherwise. `WARN` and `INFO` findings are reported on stderr but do not fail the run.

## Output format

Each finding is one line on stderr in the shape:

```text
[SEVERITY] category: message
```

The final line on stdout (or stderr on failure) summarizes counts:

```text
Harness validation passed (errors=0 warns=2 infos=0)
```
