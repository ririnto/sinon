# CI Hooks Build Integration

Open this reference when wiring configured harness checks into GitHub Actions, GitLab CI, git hooks, Gradle `check`, Node package scripts, or standalone check lanes.

## Provider selection

Use `ci.provider` from `docs/harness-engineering/harness-engineering.json`.

| Provider | File | Asset |
| --- | --- | --- |
| `github-actions` | `.github/workflows/harness-checks.yml` | `assets/github-actions.yml` |
| `gitlab-ci` | `.gitlab-ci.yml` | `assets/gitlab-ci.yml` |
| `none` | none | skip CI install |

Do not install both provider files unless the target config explicitly declares both as project-specific checks.

## Stage gates

- Stage 2: hooks run advisory checks and print warnings for known violations.
- Stage 3: CI, Gradle, Node package scripts, or standalone lanes fail on `error` findings and malformed harness config.
- Stage 4: ratchets reduce the known-violation budget and fail doc freshness gates.

## GitHub Actions wiring

Copy `assets/github-actions.yml` and keep the validator command aligned with `checks.requiredCommands`.

```yaml
name: harness-checks

on:
  push:
    branches: [develop, main, master]
  pull_request:
    branches: [develop, main, master]
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
      - name: Verify uvx is provisioned
        run: uvx --version
      - name: Validate harness
        run: sh scripts/harness/validate_harness.sh
```

## GitLab CI wiring

Copy `assets/gitlab-ci.yml` and keep it provider-neutral except for GitLab syntax.

```yaml
stages: [check]

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
    - uvx --version
  script:
    - sh scripts/harness/validate_harness.sh
```

If a target repo adds third-party actions or container images to these templates, pin them before Stage 3 error gates are enabled.

The default branch list is `develop`, `main`, and `master`. Adjust branch lists, path filters, protected-branch logic, or provider rules to match user preference before enabling error gates.

## Git hooks

Use hooks as fast local guardrails, not as the only source of enforcement.

```bash
git config core.hooksPath .githooks
```

Commit hooks should validate commit-message shape only when the target repo declares that policy. Pre-push hooks should run the offline validator and any fast configured checks. Hooks must not require network access.

## Gradle integration

Use Gradle only when `gradle.enabled` is true.

```kotlin
tasks.register<Exec>("harnessCheck") {
    group = "verification"
    description = "Validate harness-engineering repository configuration."
    commandLine("sh", "scripts/harness/validate_harness.sh")
}
tasks.named("check") {
    dependsOn("harnessCheck")
}
```

Prefer conditional wiring in multi-module builds: only attach `harnessCheck` to `check` for projects where the required plugin or source layout exists. Mature Gradle builds commonly expose both a fast local precommit/QA task and a `check` dependency for shared CI gates.

## Node integration

Use Node package scripts only when `node.enabled` is true or the target repo explicitly wants package-manager integration.

```json
{
  "scripts": {
    "harness:validate": "sh scripts/harness/validate_harness.sh",
    "harness:check": "sh scripts/harness/validate_harness.sh"
  }
}
```

Use the package manager already declared by the target repository. Prefer the explicit `run` form because it works across npm, pnpm, Yarn, and Bun: `npm run harness:validate`, `pnpm run harness:validate`, `yarn run harness:validate`, or `bun run harness:validate`.

For workspaces, keep the root `harness:validate` script as the stable CI entrypoint and delegate internally to the target's orchestrator, such as pnpm recursive runs, Yarn workspaces, npm workspaces, Turbo, or Nx.

## Failure handling

- CI failure stops merge when Stage 3 is enabled and it reflects malformed config, missing declared files, broken symlinks, or new `error` guardrail findings.
- Hook failure stops local commit or push only for policies that the target repo has promoted from `warn` to `error`; otherwise it prints the same remediation path as CI.
- Build integration failure should be treated as a normal verification failure under the selected lifecycle (`check`, package script, standalone lane, or CI job).
- Provider mismatch is fixed by updating `docs/harness-engineering/harness-engineering.json` first, then changing files.
- The bundled validator enforces execution against trusted argv forms only. Review any changed `checks.requiredCommands` or `checks.optionalCommands` before running them, and treat `checks.allowedCommandPrefixes` as human-readable documentation only.
