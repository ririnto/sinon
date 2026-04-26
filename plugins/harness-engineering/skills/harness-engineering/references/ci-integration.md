# CI Integration

Open this reference when wiring the harness-engineering checks into a CI provider, scheduling the doc-gardening agent, or defining the auto-merge policy for cleanup pull requests. The common-path guidance in `SKILL.md` describes the checks themselves; this reference covers how to run them on every push and on a schedule.

## Required jobs

Every push and every pull request MUST run, at minimum:

- Structural tests for the layer model and provider interface.
- Custom linters for import direction, structured logging, file size, naming conventions, and boundary parsing.
- The language's native test runner for unit and integration tests.
- The cross-link checker for `CLAUDE.md` and `docs/` index files.

Scheduled jobs SHOULD additionally run:

- The doc-gardening agent at least once per day.
- A golden-principles sweep at least once per week.
- A full architecture-compliance check per release.

## GitHub Actions sample

The pipeline below assumes Python tooling for the linters and structural tests. Adapt the runner image, cache keys, and install command for the repository's language.

```yaml
name: harness-checks

on:
  push:
    branches: ["**"]
  pull_request:
    branches: ["**"]
  schedule:
    - cron: "0 9 * * *"

jobs:
  structural:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
      - name: Install tools
        run: pip install -r tools/requirements.txt
      - name: Run structural tests
        run: pytest tools/structural_tests --maxfail=1
      - name: Run custom linters
        run: python tools/lint.py --all
      - name: Check cross-links
        run: python tools/check_links.py

  tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
      - name: Install tools
        run: pip install -r tools/requirements.txt
      - name: Run test suite
        run: pytest

  doc-gardening:
    if: github.event_name == 'schedule'
    runs-on: ubuntu-latest
    permissions:
      contents: write
      pull-requests: write
    steps:
      - uses: actions/checkout@v4
      - name: Run doc-gardening agent
        env:
          AGENT_RUNTIME_TOKEN: ${{ secrets.AGENT_RUNTIME_TOKEN }}
        run: python tools/agents/run_doc_gardener.py
```

Key invariants:

- The `schedule` trigger runs the doc-gardening job without blocking regular CI on the cron cadence.
- Structural tests and linters fail the pipeline on any violation; their error messages carry remediation text so the agent can self-correct.
- The doc-gardening job is permitted to open pull requests but MUST NOT push to protected branches directly.

## GitLab CI sample

```yaml
stages: [check, test, garden]

structural:
  stage: check
  image: python:3.12
  script:
    - pip install -r tools/requirements.txt
    - pytest tools/structural_tests --maxfail=1
    - python tools/lint.py --all
    - python tools/check_links.py

tests:
  stage: test
  image: python:3.12
  script:
    - pip install -r tools/requirements.txt
    - pytest

doc-gardening:
  stage: garden
  image: python:3.12
  rules:
    - if: $CI_PIPELINE_SOURCE == "schedule"
  script:
    - python tools/agents/run_doc_gardener.py
```

## Auto-merge policy

Cleanup pull requests from the doc-gardening and golden-principles sweeps SHOULD be eligible for auto-merge. A pull request MAY auto-merge only when all of the following hold:

- The PR touches exclusively `docs/` or the cleanup targets declared by the sweep.
- The change is under the declared line budget (for example, 40 changed lines).
- Every required check has passed.
- The `code-reviewer` agent has returned `approve` as its verdict.
- No human has applied a `hold` label within the configured review window.

Express the policy as a repository ruleset rather than as reviewer discipline. Example GitHub merge-queue configuration:

```yaml
merge_queue:
  require_checks:
    - harness-checks / structural
    - harness-checks / tests
  require_review:
    - code-reviewer
  merge_method: squash
  max_entries_to_merge: 5
```

## Failure handling

- A flaky test MUST NOT be treated as a blocker for more than one follow-up run. If a flake recurs, open a tech-debt item in `docs/exec-plans/tech-debt-tracker.md` and gate only the affected domain until it is fixed.
- A linter regression MUST block merge. Linter exceptions MUST be encoded into the linter itself, never suppressed in comments or PR descriptions.
- A structural-test regression MUST block merge unconditionally. These tests guard the layer model; reversing one is equivalent to abandoning the architecture.

## Common mistakes

- Running structural tests only on the default branch. Violations reach the default branch silently because CI does not catch them on feature branches.
- Letting the doc-gardening job run without permissions to open pull requests. The job produces reports but cannot act on them, so drift accumulates.
- Allowing manual merge overrides without recording the reason. Overrides become ambient permission to bypass the harness.
- Treating auto-merge as a convenience toggle. Without a declared line budget and a reviewer verdict gate, auto-merge turns into unreviewed merge.

