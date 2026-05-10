# CLAUDE.md

Repository-level instruction map for {project-name}. Keep detailed guidance in the referenced docs and update those docs when implementation reality changes.

## Start Here

- Read `ARCHITECTURE.md` before changing source structure or dependency direction.
- Read `docs/harness/guardrails.md` and `docs/harness/readiness.md` before changing validation, CI, hooks, or repository policy.
- Read `docs/product-specs/` before changing product behavior.
- Read `docs/design-docs/` and topic docs under `docs/` before changing design, frontend, reliability, quality, or security behavior.
- Read `docs/exec-plans/active/` before continuing planned work.
- Read `docs/references/` only when a referenced technology applies.

## Project

{project-name} is {one-sentence description of the project purpose and scope}.

## Tech Stack

- Language: {language and minimum supported version}
- Runtime: {runtime, deployment target, or hosted platform}
- Package manager: {package-manager and lockfile policy}
- Framework: {framework and major version}

## Commands

```bash
{install-command}
{build-command}
{test-command}
{lint-command}
{format-command}
```

## Conventions

- {convention: naming, module boundaries, or dependency direction}
- {convention: required tests or evidence before merge}
- {convention: branching, release, or incident workflow}

## Owners

| Area | Owner |
| --- | --- |
| {area} | {owner} |
