# Bootstrap

Open this reference when seeding an empty git repository with an agent-first harness. The common-path guidance in `SKILL.md` assumes the knowledge base, layer model, and enforcement hooks already exist. This reference covers the zero-to-one path that creates them.

## Preconditions

- An empty git repository with at least one initial commit.
- A coding agent runtime with file-system and shell access inside that repository.
- A minimum set of starter templates the repository can anchor on (language runtime, package manager, application framework, CI provider).

## Sequencing

Bootstrap MUST proceed in the order below. Each step produces artifacts the next step depends on.

1. Seed the knowledge map (`CLAUDE.md` and `AGENTS.md` symlink).
2. Scaffold the `docs/` directory with the canonical top-level files and index stubs.
3. Generate the initial application skeleton from the starter templates.
4. Encode the layer model and providers interface as directories and a structural test.
5. Add the first custom linters (import direction, structured logging, file size).
6. Wire CI to run the structural test and linters on every push.
7. Commit the first execution plan describing what comes next.

## Starter prompt

Use a single prompt so the agent produces the whole bootstrap as one change set. Adapt the placeholders in braces.

```text
Bootstrap an agent-first repository in the current directory.

Constraints:
- Language: {language}
- Package manager: {package-manager}
- Application framework: {framework}
- CI provider: {ci-provider}

Deliver, in order:
1. CLAUDE.md as a table of contents under 150 lines, with AGENTS.md as a
   symlink to CLAUDE.md.
2. docs/ with ARCHITECTURE.md, DESIGN.md, FRONTEND.md, PLANS.md,
   PRODUCT_SENSE.md, QUALITY_SCORE.md, RELIABILITY.md, SECURITY.md,
   design-docs/index.md, design-docs/core-beliefs.md,
   exec-plans/active/, exec-plans/completed/,
   exec-plans/tech-debt-tracker.md, generated/, product-specs/index.md,
   references/.
3. The application skeleton for {framework} with one sample domain that
   follows Types -> Config -> Repo -> Service -> Runtime -> UI and a
   providers/ subtree exposing a single interface module.
4. A structural test that fails when any domain lacks the required layer
   directories or when any import violates the declared layer model.
5. Custom linters for import direction, structured logging, and file
   size limits, with error messages that include remediation instructions.
6. A CI pipeline for {ci-provider} that runs the structural test, the
   linters, and the language's native test runner on every push and
   pull request.
7. docs/exec-plans/active/0001-bootstrap.md describing this bootstrap as
   the first execution plan, with a decision log entry for each major
   choice above.

Produce the change as a single commit on a new branch. Open a pull
request when finished.
```

## Symlink creation

`AGENTS.md` MUST be a symlink to `CLAUDE.md`. Do not duplicate content.

```sh
ln -s CLAUDE.md AGENTS.md
git add AGENTS.md CLAUDE.md
```

## Minimum viable structural test

The first structural test MUST reject any domain that is missing a required layer directory. This is what lets subsequent agent runs trust the layer model.

```python
"""
Structural test: every business domain follows the fixed layer set.

:raises AssertionError: when a domain is missing required layer
    directories, or when a directory exists outside the allowed set.
"""
from pathlib import Path

REQUIRED_LAYERS = {"types", "config", "repo", "service", "runtime", "ui"}
ALLOWED_EXTRA = {"providers"}

def test_domain_structure(domains_root):
    for domain in Path(domains_root).iterdir():
        if not domain.is_dir():
            continue
        actual = {d.name for d in domain.iterdir() if d.is_dir()}
        missing = REQUIRED_LAYERS - actual
        extra = actual - REQUIRED_LAYERS - ALLOWED_EXTRA
        assert not missing, (
            f"Domain '{domain.name}' is missing layers: {missing}. "
            f"Required layers: {sorted(REQUIRED_LAYERS)}."
        )
        assert not extra, (
            f"Domain '{domain.name}' has unexpected directories: {extra}. "
            "Only the standard layers and 'providers' are allowed."
        )
```

## First execution plan

The first execution plan MUST be committed so future agent runs can see why the bootstrap looks the way it does. Copy `assets/execution-plan-template.md` and fill in:

- Problem: "The repository is empty and needs an agent-first harness."
- Approach: summary of the seven bootstrap steps.
- Decision log: one row per major technology choice, each with a one-line rationale.
- Progress: a single row stating that bootstrap completed.
- Status: `completed`. Move the file to `docs/exec-plans/completed/` once the PR merges.

## Completion checks

Before closing the bootstrap loop, confirm all of the following:

- `CLAUDE.md` exists, is under 150 lines, and points at `docs/` only.
- `AGENTS.md` is a symlink and resolves to `CLAUDE.md`.
- `docs/` contains every canonical top-level file and the required subdirectories.
- One sample domain demonstrates the layer model end to end.
- The structural test and at least three custom linters run in CI and pass.
- `docs/exec-plans/active/` or `docs/exec-plans/completed/` contains the bootstrap plan.

If any check fails, fix the harness before merging the bootstrap PR. The harness is what every later agent run depends on; silent gaps compound quickly.

## Common mistakes

- Writing application logic before the layer model and its structural test exist. Later agent runs will pattern-match against whatever shipped first.
- Shipping a monolithic `CLAUDE.md` that embeds guidance instead of pointing to `docs/`. See `references/repository-knowledge-structure.md` for the progressive disclosure model.
- Skipping CI wiring. Without CI, the structural test and linters are advisory only, and drift starts on day one.
- Skipping the first execution plan. Future agents cannot reason about why the bootstrap looks the way it does if the decisions are not checked in.
