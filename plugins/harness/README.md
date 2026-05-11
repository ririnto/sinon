# Harness

Harness is a Claude Code plugin for installing, validating, and evolving repository-owned agent development scaffolding. It combines the v6 archive structure with this repository's plugin packaging rules: skills are the declared runtime surface, root agents are host-dependent and undeclared in the manifest, and target repositories own the files copied into them.

## Purpose

- Install a repository harness with `AGENTS.md`, `CLAUDE.md`, `ARCHITECTURE.md`, a structured `docs/` tree, `.claude/` project agents, `.claude/` project skills, templates, validation adapters, CI snippets, and opt-in Git hook templates.
- Keep implementation, repository docs, product specs, execution plans, generated references, and deterministic checks evolving together so agents work from versioned context rather than hidden convention.
- Preserve harness-only development readiness: the scaffold gives agents a working operating surface, but target repositories still supply product requirements, architecture decisions, source code, runtime configuration, secrets, and domain references.
- Provide workflow templates with bounded concurrency, handoff states, workspace policies, prompt contracts, and proof-of-work expectations so agents can participate in ticket-level orchestration without requiring a daemon or issue poller.

## Lifecycle

1. Install repository-owned harness files into a target repository.
2. Validate the installed context with the target repository's matching stack command.
3. Evolve templates, docs, agents, skills, generated-artifact policy, and validation rules as project reality changes.

## Install in Claude Code

Install from this repository checkout:

```sh
claude plugin install ./plugins/harness --scope project
```

## Runtime Surface

- Skills: `harness-install`, `harness-validate`, and `harness-evolve`.
- Agents: `harness-architect`, `harness-reviewer`, and `harness-validator` under `agents/` for host runtimes that load plugin agents from the plugin root. Hosts that do not load plugin-root agents still receive the skill surface.
- Scripts: `scripts/plugin-self-check.sh` for local plugin package validation.
- Target templates: repository-owned files under `skills/harness-install/templates/`, including `.claude/agents`, `.claude/skills`, `.claude/harness`, docs, CI, validation adapters, and Git hook scaffolds.

The Claude Code manifest declares only `./skills/`. Agents remain at the plugin root and are described here rather than declared in `.claude-plugin/plugin.json` because this repository's manifest rules prohibit an `agents` key. The archive's empty top-level `hooks` surface is intentionally omitted; the packaged hook scaffold lives under `skills/harness-install/templates/common/.claude/harness/git-hooks/`, and the installer generates the target hook template from the selected mode.

## Target Ownership

Target repositories own every installed harness file. Copied docs, scripts, CI files, hooks, agents, skills, templates, and validation adapters MAY be edited, renamed, or removed to fit the target project. The installed `.claude/harness/manifest.json` is the target-local harness metadata contract; it replaces the old `docs/harness/config.json` convention used by earlier drafts of this plugin.

## Install Harness Assets

From a target repository, use the `harness-install` skill with a stack argument:

```text
auto
```

The skill invokes `skills/harness-install/scripts/install-harness.sh` with the target repository as `--target`, detects or accepts the stack mode, copies repository-level files and `.claude/` assets, then prints the stack-specific validation command. Supported modes are `auto`, `gradle`, `maven`, `uv`, and `bun`.

By default the installer writes both GitHub Actions and GitLab CI examples for the selected stack: `.github/workflows/harness.yml` and `.gitlab-ci.yml`. Both CI snippets are rendered from templates and run the same selected validation command printed by the installer. Pass `--no-ci` to skip both CI files.

To run the installer directly, pass the target repository explicitly:

```sh
sh /path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.sh --target /path/to/target-repo --mode auto --hooks none
```

## Required Repository Structure

The installer creates and validators require this repository context structure:

```text
AGENTS.md
ARCHITECTURE.md
CLAUDE.md
.claude/
├── agents/
│   ├── harness-implementation-agent.md
│   ├── harness-orchestrator.md
│   └── harness-review-agent.md
├── skills/
│   ├── harness-orchestrate/
│   │   └── SKILL.md
│   ├── harness-review/
│   │   └── SKILL.md
│   └── harness-validate/
│       └── SKILL.md
└── harness/
    ├── git-hooks/
    │   └── pre-commit
    ├── manifest.json
    └── templates/
docs/
├── design-docs/
│   ├── index.md
│   └── core-beliefs.md
├── exec-plans/
│   ├── active/
│   │   └── .gitkeep
│   ├── completed/
│   │   └── .gitkeep
│   └── tech-debt-tracker.md
├── generated/
│   └── .gitkeep
├── product-specs/
│   ├── index.md
│   └── new-user-onboarding.md
├── references/
│   ├── design-system-reference-llms.txt
│   ├── nixpacks-llms.txt
│   └── uv-llms.txt
├── DESIGN.md
├── FRONTEND.md
├── PLANS.md
├── PRODUCT_SENSE.md
├── QUALITY_SCORE.md
├── RELIABILITY.md
└── SECURITY.md
```

Empty required directories are kept in version control with `.gitkeep`. `.claude/harness/git-hooks/pre-commit` is a generated, target-owned hook template containing the selected validation command, not an active Git hook unless the target repository opts in. `docs/generated/` is a generated-artifact location, not a required database-documentation location. Generated artifacts SHOULD document their source command, source inputs, freshness, and regeneration trigger.

In installed target repositories, `AGENTS.md` is the primary harness contract. `CLAUDE.md` is retained as the Claude Code entry point and points back to `AGENTS.md`.

## Validation Adapters

| Stack | Detection | Validation command |
| --- | --- | --- |
| Gradle | `settings.gradle(.kts)` or `build.gradle(.kts)` | `./gradlew harnessValidate`, or `gradle harnessValidate` when the target uses system Gradle without a wrapper |
| Maven | `pom.xml` | `mvn -q -f .claude/harness/maven-plugin/pom.xml install && mvn -q ai.harness:harness-maven-plugin:0.1.0:validate` |
| uv | `uv.lock` or Python `pyproject.toml` | `uv run python .claude/harness/uv/harness_validate.py` |
| bun | `bun.lock`, `bun.lockb`, or `package.json` | `bun run .claude/harness/bun/harness-validate.ts` |

Run validation commands from the target repository root. The uv, bun, and Maven validators bind that current directory as the target root, and native validators compare the installed `.claude/harness/manifest.json` fields that this plugin writes.

Gradle installer wiring prepends a composite build include for `.claude/harness/gradle-plugin`. Complex existing `settings.gradle(.kts)` files, especially those with custom plugin management or composite builds, SHOULD be reviewed manually after installation.

## Git Hooks

The installer writes a selected-mode hook template in `.claude/harness/git-hooks/` on fresh install and preserves an existing target-owned template on refresh unless `--force` is used. Git hook activation is opt-in because it modifies local Git behavior outside version control.

```sh
sh /path/to/sinon/plugins/harness/skills/harness-install/scripts/install-harness.sh --target /path/to/target-repo --mode auto --hooks copy
```

Use `--hooks none` or omit the flag to skip Git hook activation. Use `--hooks copy` only when the target should activate the generated pre-commit check.

Copy mode keeps an existing active hook unless `--force` is used. With `--force`, the installer replaces the active hook with installer-generated content for the selected mode. Use `--hooks copy --force` only with explicit approval because it changes local Git behavior.

## Metadata and Attribution

- Manifest author: `ririnto`, matching the repository owner metadata.
- Plugin license: Apache-2.0, recorded in `LICENSE`.
- External inspiration and adapted taxonomy are documented in `THIRD_PARTY_NOTICES.md`.
- Marketplace releases are versioned at `.claude-plugin/marketplace.json`; plugin manifests intentionally omit `version`.

## Harness Evolution

The installed harness MAY evolve as the project moves through discovery, implementation, hardening, release, and maintenance. Treat the current committed harness files as the active target contract. Use `harness-evolve` when repeated validation or review failures show that templates, docs, agents, skills, generated-artifact locations, or validation rules should change.

## Layout

```text
plugins/harness/
├── .claude-plugin/plugin.json
├── LICENSE
├── README.md
├── THIRD_PARTY_NOTICES.md
├── agents/
├── scripts/
└── skills/
    ├── harness-evolve/
    ├── harness-install/
    └── harness-validate/
```

## Scope Notes

- This plugin prepares repository knowledge, guardrails, templates, and validation paths. It does not run background services or manage issue queues.
- Target-owned agents are starting points, not immutable plugin internals.
- Target-local `.claude/skills/harness-validate` is installed as a project skill. If a host exposes both plugin skills and project skills with the same name, follow the host's normal project-local precedence or rename the target-local skill after installation.
- GitHub Actions and GitLab CI templates use ordinary version tags from the archive; projects with strict supply-chain policy SHOULD pin actions and images to reviewed immutable references after installation.
- Maven and Gradle self-checks may create IDE/build metadata under template directories; repository `.gitignore`, installer filters, and plugin self-checks exclude `.classpath`, `.project`, `.factorypath`, `.settings/`, `.gradle/`, `bin/`, `build/`, and `target/` while leaving source template files trackable.
