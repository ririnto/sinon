---
metadata:
  reference:
    Dependabot:
      url: https://docs.github.com/en/code-security/dependabot/dependabot-version-updates/configuration-options-for-the-dependabot.yml-file
---

# uv Tool Reference

This document owns the `uv` profile commands, dependencies, and target integration rules for Python targets.
It accompanies the [Python language rules](../languages/python.md) and [shared rules](../rules.md).
Native configuration sources live under `tooling/uv/`.

## Scope And Detection

Select the `uv` profile for a root that has `pyproject.toml` with a `[tool.uv]` table or a `uv.lock` file.
A `pyproject.toml` alone does not prove uv; an explicit `uv` selection always wins over detection.
The profile requires an existing project managed by uv and never invents project identity.
For a new empty target, report the initialization gap and run only `uv init` when the user asks for project creation.

## Toolchain

- uv: check the official releases for the latest stable version compatible with the target, then use the [official standalone installer](https://docs.astral.sh/uv/getting-started/installation/).
- Python: the target's `requires-python` field stays authoritative.
- Ruff: `>= 0.16.8,<0.17` through the `dev` dependency group.
- pre-commit: `>= 4.6.2,<5` through the `dev` dependency group, hooks only when explicitly selected; this pre-commit line requires Python >= 3.10 in the target environment (4.2.x required >= 3.9).

These bounds record the source profile's selected versions, not the latest PyPI releases.
Before adding or upgrading Ruff or pre-commit, check PyPI for the latest stable version compatible with the target's `requires-python` and existing constraints.
Honor a newer target-pinned version instead of downgrading it.
Review the target manifest and lockfile before widening a `0.x` minor bound.
Dependabot does not scan arbitrary fragments, so the target's merged `pyproject.toml` remains the native update manifest.
Do not install global Python toolchains to fill a local gap; name the gap instead.

## Native Configuration Sources

| Source file | Destination | Write rule |
| --- | --- | --- |
| `tooling/uv/pyproject.fragment.toml` | merge into existing `pyproject.toml` | merge only the `[tool.uv]` and `[dependency-groups]` tables |
| `tooling/uv/ruff.toml` | `ruff.toml` at the project root | create; merge keys when a Ruff config already exists |
| `tooling/uv/.pre-commit-config.yaml` | `.pre-commit-config.yaml` at the project root | create only when hooks are selected; merge `repos` entries when a file exists |
| `tooling/uv/.editorconfig` | merge into existing `.editorconfig` | merge only missing sections and keys |

Merge never replaces a whole manifest.
Keep the target's `project` table, name, version, and dependencies untouched.
Never convert a real Python package into a virtual project: when the target declares `package = true`, or builds a distributable wheel or sdist, the existing value stays.
Add `[tool.uv] package = false` only when the target has no `[tool.uv]` table and is an application environment.
Merge `[dependency-groups]` by group name: add a missing group, and add missing requirements to an existing group; never replace or drop the target's own group entries.
If the target already owns hooks (for example an existing `repos` entry), keep that entry and add nothing unless the user selected hooks.

## Validation Commands

Run inside the project root after `uv sync`:

```sh
uv run ruff check .
uv run ruff format --check .
```

`ruff format --check` never rewrites files.
`uv run ruff check --fix` applies safe lint fixes.
There is no bundled Python test runner; use the target's own test command when it has one.

## CI Behavior

The GitHub catalog `ci/github/uv.yaml` and GitLab catalog `ci/gitlab/uv.gitlab-ci.yaml` run the same two checks in one job against the project root.
Before adopting catalog action or image versions, check their official releases against the target's Python baseline and CI policy.
Keep compatible target pins.
A working-directory adjustment is required when the project is not at the repository root; set the job's working directory instead of changing the commands.
The GitLab job declares `stage: validate`; add that stage to the target's pipeline stages when it does not exist.

## Known Limitations

- A local run needs the uv executable; when it is missing, report the gap and run nothing in its place.
- pre-commit hooks execute third-party and local programs at commit time; review them before activation and never activate them silently.
- `uv.lock` is generated output; never copy it from this plugin.
