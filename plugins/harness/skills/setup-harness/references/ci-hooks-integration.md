# CI Hooks Integration

Open this reference when wiring configured harness checks into GitHub Actions, GitLab CI, or git hooks.

## Provider selection

Use `gates.ci.provider` from `docs/harness/config.json`.

| Provider | File | Asset |
| --- | --- | --- |
| `github-actions` | `.github/workflows/harness-checks.yml` | `assets/github-actions.yml` |
| `gitlab-ci` | `.gitlab-ci.yml` | `assets/gitlab-ci.yml` |
| `none` | none | skip CI install |

Do not install both provider files unless the target config explicitly declares both as project-specific checks.

## Stage gates

- Stage 2: hooks run advisory checks and print warnings for known violations.
- Stage 3: CI or hooks fail on `error` findings and malformed harness config.
- Stage 4: ratchets reduce the known-violation budget and fail doc freshness gates.

## GitHub Actions wiring

Copy `assets/github-actions.yml` and keep the validator command aligned with `commands.required`.

```yaml
name: harness-checks

on:
  push:
    branches:
      - develop
      - main
      - master
  pull_request:
    branches:
      - develop
      - main
      - master
  workflow_dispatch:

permissions: {}

concurrency:
  group: ${{ github.workflow }}-${{ github.event.pull_request.number || github.sha }}
  cancel-in-progress: true

jobs:
  harness:
    runs-on: ubuntu-latest
    permissions:
      contents: read
    timeout-minutes: 10
    steps:
      - name: Checkout repository without third-party actions
        run: |-
          git init .
          git remote add origin "$GITHUB_SERVER_URL/$GITHUB_REPOSITORY"
          git fetch --depth=1 origin "$GITHUB_SHA"
          git checkout --detach FETCH_HEAD
      - name: Verify uv-managed Python is provisioned
        run: uv --version
      - name: Validate harness
        run: sh scripts/harness/validate_harness.sh
```

## GitLab CI wiring

Copy `assets/gitlab-ci.yml` and keep it provider-neutral except for GitLab syntax.

```yaml
stages:
  - check

workflow:
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH =~ /^(develop|main|master)$/'
    - when: never

harness:
  stage: check
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_PIPELINE_SOURCE == "push" && $CI_COMMIT_BRANCH =~ /^(develop|main|master)$/'
  before_script:
    - uv --version
  script:
    - sh scripts/harness/validate_harness.sh
```

If a target repo adds third-party actions or container images to these templates, pin them before Stage 3 error gates are enabled.

The default branch list is `develop`, `main`, and `master`. Adjust branch lists, path filters, protected-branch logic, or provider rules to match user preference before enabling error gates.

## Git hooks

Use hooks as fast local guardrails, not as the only source of enforcement.

```bash
sh scripts/harness/setup-hooks.sh
```

Commit hooks should validate commit-message shape only when the target repo declares that policy. Pre-push hooks should run the offline validator and any fast configured checks. Hooks must not require network access.

## Optional integration packs

The core validator has one trusted default command: `sh scripts/harness/validate_harness.sh`. Use these packs only when the target repository explicitly wants a toolchain-native entrypoint for the same versioned harness contract.

| Pack | Asset path | Target entrypoint |
| --- | --- | --- |
| Bun validator | `assets/bun-validator/validate-harness.mjs` | `bun scripts/harness/validate-harness.mjs` |
| Gradle plugin | `assets/gradle-plugin/` | `./gradlew harnessCheck` |
| Maven plugin | `assets/maven-plugin/` | `mvn local.harness:harness-maven-plugin:harness-check` |

Bun support is a no-dependency Bun validator. Gradle and Maven support are copy/adapt plugin assets, not broad build lanes in the core config validator. If a target repository promotes one of these commands to CI, document the adopted pack and exact command before enabling error gates.

## No network downloads

CI and hooks must use repository-local files and pre-provisioned tools. Do not add network downloads as part of ordinary harness validation.

## Command output

Do not hide command output with shell redirects. Use visible command output or a tool's quiet flag when the check supports one.
