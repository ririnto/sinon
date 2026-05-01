---
name: spec-driven-development
description: >-
  Drive implementation through a SPEC.md-first workflow covering research, review gates, approved-spec execution, and completeness verification. Use this skill when the user explicitly asks to follow a spec-driven workflow, write a SPEC.md before implementing, create a specification, define requirements before coding, or use a spec-first approach. Covers the full lifecycle: research external unknowns, author SPEC.md, pass review gates, implement against the approved spec, and verify completeness. Activated only on explicit user request, not automatically.
license: Apache-2.0
---

# spec-driven-development

Drive work through approved `spec/` artifacts before implementation.
Treat `SPEC.md` as the source of truth for scope, intended behavior, and externally meaningful constraints.

Use the current working repository as the destination for `spec/` outputs.
Use the installed skill root only as the source for bundled scripts and templates.

When this skill is installed as a plugin, resolve the installed paths like this:

```bash
PLUGIN_ROOT="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must point to the installed plugin root}"
SKILL_ROOT="${PLUGIN_ROOT}/skills/spec-driven-development"
```

If your host does not provide `${CLAUDE_PLUGIN_ROOT}`, replace `SKILL_ROOT` with the absolute path to the installed `spec-driven-development` skill directory.

## Operating rules

- MUST use `spec/domain/<ownership-path>/SPEC.md` for capability specs, where `<ownership-path>` reflects the owning capability boundary.
  MUST NOT use `topic`, `policy`, `audit`, or `repository-improvement` unless they map to a real owning capability.
- MUST use `spec/research/{framework|library|topic}/{name}/RESEARCH.md` for research artifacts.
- MUST use `RESEARCH.md` only for framework, library, or topic investigation that informs later spec decisions.
  MUST NOT use `RESEARCH.md` for project comparison, repository audits, implementation planning, migration sequencing, or task management.
- MUST keep `call` entries SPEC-to-SPEC only, using relative paths to existing `SPEC.md` files.
  MUST keep `call: []` when a SPEC has no outbound dependencies.
- MUST treat `SPEC.md` as the source of truth for implementation scope.
- MUST keep `SPEC.md` focused on abstract requirements, intended behavior, boundaries, and externally meaningful constraints.
  SHOULD avoid introducing language, framework, library, or code-style constraints unless the user explicitly requests them or verified external constraints make them necessary.
- MUST author SPEC artifacts by flow `spec -> code`.
  MUST NOT reverse-derive spec content from current implementation.
- MUST NOT create or modify Git branches.
- MUST NOT reset or overwrite in-progress plan documents without user confirmation.
- MUST NOT create backup files.
- MUST run `"${SKILL_ROOT}/scripts/sdd.sh" validate <spec-root-or-subtree>` before Spec Review closes and again after the final spec sync when `uv` is available on the host and can resolve its Python runtime plus required dependency/build artifacts from local cache or local files.
  When `uv` is unavailable or cannot run from locally available inputs, MUST document the runtime blocker in the review record and MUST complete every applicable inline-checklist item manually in place of the validator result.
- MAY run `npx -y markdownlint-cli2 <touched-markdown-files>` only when the maintainer or consuming repository already uses markdownlint.
  Markdown linting is OPTIONAL maintenance guidance, not a prerequisite for ordinary offline use of this skill.

## Package surface

Offline prerequisite: `sdd.sh` shells out to `uvx`, which is part of [uv](https://github.com/astral-sh/uv). The validator is the preferred Spec Review gate when `uv` is installed on the host and can resolve Python plus required dependency/build artifacts from local cache or local files. When `uv` is missing or cannot run without fetching those inputs, fall back to the manual inline-checklist path documented in the Ordinary offline-capable workflow and Review gates sections.

Use these bundled paths from `SKILL_ROOT`:

- `./scripts/sdd.sh` — single CLI entrypoint that dispatches to all SDD toolkit subcommands. The shipped subcommands are:
  - `validate <spec-root>` — validate a `spec/` tree or subtree (default Spec Review gate)
  - `list-frontmatter [spec-path]` — frontmatter inventory and inbound-call queries
  - `get-frontmatter <kind> <path>` — read one artifact frontmatter block
  - `generate-diagram [spec-root]` — generate Mermaid relationship diagrams from SPEC links
  - `list-tags [spec-path]` — aggregate tag inventory across the tree
- `./scripts/sdd/` — Python package that implements the CLI.
- `./scripts/pyproject.toml` — sibling of `./scripts/sdd/`; declares the `sdd` console script entry point that `sdd.sh` invokes via `uvx --from "${script_dir}"`.
- `./assets/templates/` — scaffolds for `SPEC.md`, `RESEARCH.md`, `CONTRACT.md`, `openapi.yaml`, and `spec/CHANGELOG.md`
- `./assets/schemas/` — schema files used by the validator
- `./references/examples/` — validator-clean examples for comparison

## Ordinary offline-capable workflow

Follow this path unless a named blocker sends you to an optional reference.

1. Decide whether research is needed.
   - Create `RESEARCH.md` only when external framework, library, or topic behavior is unclear or version-sensitive.
   - Skip research when the capability can be specified from already-known product behavior.
2. Create the required scaffolds in the current working repository without overwriting existing authored files.

   ```bash
    PLUGIN_ROOT="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must point to the installed plugin root}"
    SKILL_ROOT="${PLUGIN_ROOT}/skills/spec-driven-development"

    mkdir -p spec/domain/service
    cp -n "${SKILL_ROOT}/assets/templates/SPEC.md" spec/domain/service/SPEC.md
    mkdir -p spec
    cp -n "${SKILL_ROOT}/assets/templates/CHANGELOG.md" spec/CHANGELOG.md
   ```

   Adjust `service` to the real ownership path.
3. Create optional scaffolds only when they materially improve clarity.

   ```bash
    PLUGIN_ROOT="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must point to the installed plugin root}"
    SKILL_ROOT="${PLUGIN_ROOT}/skills/spec-driven-development"

    cp -n "${SKILL_ROOT}/assets/templates/CONTRACT.md" spec/domain/service/CONTRACT.md
    cp -n "${SKILL_ROOT}/assets/templates/openapi.yaml" spec/domain/service/openapi.yaml
    mkdir -p spec/research/library/react-router
    cp -n "${SKILL_ROOT}/assets/templates/RESEARCH.md" \
      spec/research/library/react-router/RESEARCH.md
   ```

   - `CONTRACT.md` is optional semantic contract depth.
   - `openapi.yaml` is optional HTTP boundary depth.
   - `RESEARCH.md` is optional and only for external investigation.
4. Author or revise `SPEC.md`.
   - Fill frontmatter first.
   - Complete `Necessity`, `Role`, and `Overview` before detailed requirements.
   - Define verifiable Functional Requirements.
   - Add `Normal Flow`, `Alternative Flow`, and `Error Flow` scenarios.
   - Add Key Entities and Constraints before review.
   - Keep requirements abstract and implementation-agnostic unless explicit or externally required constraints justify more detail.
5. If research exists, keep it evidence-oriented.
   - State the question, scope boundary, and non-goals first.
   - Separate confirmed facts from hypotheses and unknowns.
   - Fill `subject.name` and `subject.version`.
   - Refresh findings before Spec Review when they materially affect requirements.
6. Ask for explicit approval before Document Linking and formal review.
   - Gate 1 passes only when the user approves scope, primary requirements, and scenario direction of the current `SPEC.md` draft.
7. Link outbound dependencies from the calling SPEC after Gate 1 passes.
   - Add only outbound `call` entries in the caller's frontmatter.
   - Use relative paths to existing `SPEC.md` targets only.
   - Do not add backlink sections or reverse-direction metadata.
   - Use `call: []` when no outbound dependency exists.
8. Run Spec Review.
   - Apply the inline review checklist in this file.
   - Validate the authored tree.

   ```bash
   "${SKILL_ROOT}/scripts/sdd.sh" validate ./spec
   ```

   - If review passes, set `SPEC.md` status to `approved` and refresh `last_updated`.
   - If review fails, return to the earlier stage that fixes the issue.
9. Implement only after Gate 2 passes.
   - Update `SPEC.md` status to `wip` when implementation starts.
   - Change source files outside `spec/`.
   - If implementation discovers a spec gap, update the relevant spec artifacts first and re-run Spec Review before continuing.
10. Run Implementation Review and final sync.
    - Verify every Functional Requirement is implemented or explicitly justified in `SPEC.md`.
    - Update `call` when dependencies change.
    - Keep relevant `RESEARCH.md`, `CONTRACT.md`, `openapi.yaml`, and `spec/CHANGELOG.md` synchronized with the implemented state.
    - Re-run validation after the final spec sync.

    ```bash
    "${SKILL_ROOT}/scripts/sdd.sh" validate ./spec
    ```

    - Mark `SPEC.md` with the correct post-implementation status and refresh `last_updated`.

## Status lifecycle

- `draft`: initial authoring has started.
- `review`: the artifact is ready for formal review.
- `approved`: scope is accepted and implementation may start.
- `wip`: implementation is in progress.
- `implemented`: implementation review and verification are complete.
- `deprecated`, `superseded`, `removed`: retirement or replacement states.

## Review gates

### Gate 1 — SPEC Setup Complete

Passes only when the user explicitly approves the scope, primary requirements, and scenario direction of the current `SPEC.md` draft.

### Gate 2 — Spec Review Passed

Passes only when both conditions are true:

- Every applicable item in the inline review checklist below is recorded as `pass` or `n/a`, with zero remaining `fail` items.
- `"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec` exits with status `0` when `uv` is available on the host and can resolve its runtime and dependency/build artifacts from local cache or local files; otherwise the review record documents the runtime blocker and every applicable inline-checklist item is recorded as `pass` or `n/a` manually.

## Inline review checklist

Record each applicable item as `pass`, `fail`, or `n/a` with rationale.

### Spec Review minimum checklist

- `SPEC.md` frontmatter includes `title`, `description`, `last_updated`, `status`, and `call`
- `SPEC.md` is placed under `spec/domain/<ownership-path>/SPEC.md`
- Functional Requirements are verifiable and covered by scenarios
- Scenarios include Normal, Alternative, and Error flows
- `SPEC.md` remains implementation-agnostic by default
- `call` links are SPEC-to-SPEC only, relative, and resolve to existing targets
- `RESEARCH.md`, when present, is limited to external framework/library/topic investigation
- `CONTRACT.md` or `openapi.yaml`, when present, stays consistent with the current SPEC
- unresolved `TODO:` markers or template placeholders are removed from authored artifacts
- `"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec` passes

### Implementation Review minimum checklist

- every Functional Requirement is implemented or explicitly justified in `SPEC.md`
- `SPEC.md` status and `last_updated` are synchronized with implementation state
- `call` links are updated when dependency relationships changed
- `RESEARCH.md`, `CONTRACT.md`, `openapi.yaml`, and `spec/CHANGELOG.md`, when present, are synchronized with the implemented state
- `spec/CHANGELOG.md` keeps the latest date first and excludes planning-only content
- `"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec` passes after final sync

## Review evidence contract

For v1, reviews MUST be recorded in reviewer or agent output.
The workflow MUST NOT require a repo-tracked `REVIEW.md`.

Each review record MUST include:

- the review type: Spec Review or Implementation Review
- the reviewed artifact scope
- one result per applicable checklist item
- each result expressed as `pass`, `fail`, or `n/a`
- a short rationale for every applicable checklist item

## First safe commands

Use these from the consuming repository after setting `SKILL_ROOT`:

```bash
PLUGIN_ROOT="${CLAUDE_PLUGIN_ROOT:?CLAUDE_PLUGIN_ROOT must point to the installed plugin root}"
SKILL_ROOT="${PLUGIN_ROOT}/skills/spec-driven-development"

"${SKILL_ROOT}/scripts/sdd.sh" validate ./spec
"${SKILL_ROOT}/scripts/sdd.sh" list-frontmatter ./spec --inbound-of spec/domain/ingest/SPEC.md
"${SKILL_ROOT}/scripts/sdd.sh" generate-diagram ./spec
```

If you need per-file Markdown linting for maintainer hygiene, run it separately and treat it as optional:

```bash
npx -y markdownlint-cli2 <touched-markdown-files>
```

## References

Open a reference only for the named blocker:

- `./references/workflow.md` — open when you need the full stage-by-stage lifecycle, entry and exit conditions, or review-loop semantics
- `./references/spec-authoring-guide.md` — open when drafting or revising detailed `SPEC.md` sections
- `./references/research-authoring-guide.md` — open when drafting or revising `RESEARCH.md`
- `./references/linking-guide.md` — open when editing `call` links or querying inbound dependencies
- `./references/review-checklist.md` — open when you need the full Spec Review or Implementation Review worksheet to record item-by-item `pass`, `fail`, or `n/a` results with rationale

## Packaged runtime maintenance

Use this plugin-local guidance when maintaining the packaged runtime and documentation boundaries:

- Keep `./scripts/sdd.sh` as the only documented CLI entrypoint.
- Keep subcommands documented as `"${SKILL_ROOT}/scripts/sdd.sh" <subcommand> ...`; do not reference retired helper scripts.
- Keep offline wording conditional on `uv` and locally available Python, dependency, and build inputs.
- Keep runtime source changes inside `./scripts/sdd/` and `./scripts/pyproject.toml`.
- Keep user-facing validation guidance paired with the manual inline-checklist fallback.

## Output contract

Return:

1. The spec artifacts created, revised, or reviewed, with relative paths under `spec/`
2. Gate 1 (SPEC Setup Complete) status: whether the user has explicitly approved scope, primary requirements, and scenario direction of the current `SPEC.md` draft
3. Gate 2 (Spec Review Passed) status: `sdd.sh validate` exit result when `uv` can run from locally available runtime and dependency/build inputs (or a documented runtime blocker), together with the inline-checklist results recorded as `pass`, `fail`, or `n/a` with rationale per applicable item
4. When verifying implementation, the drift summary between the approved specification and the shipped code, including missing requirements, undocumented behavior, and scope drift
5. Any remaining blockers, failed checklist items, or approval needs that prevent the next gate from closing or that block implementation or release
