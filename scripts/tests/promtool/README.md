---
metadata:
  reference:
    Bun Archive:
      url: https://github.com/oven-sh/bun/blob/main/docs/runtime/archive.mdx
    Bun Hashing:
      url: https://github.com/oven-sh/bun/blob/main/docs/runtime/hashing.mdx
    Prometheus:
      version: 3.15.0
      url:
        - https://github.com/prometheus/prometheus/releases/tag/v3.15.0
        - https://github.com/prometheus/prometheus/releases/download/v3.15.0/sha256sums.txt
        - https://github.com/prometheus/prometheus/blob/v3.15.0/docs/configuration/unit_testing_rules.md
---

# Prometheus Rule Tests

Run from the repository root:

```sh
bun run test:promtool
```

The runner checks the rule file and then runs its unit test fixture.
`package.json` owns the release pin in `config.promtoolVersion`.
When updating the pin, use the latest compatible stable official release and rerun the native fixture checks.
The runner downloads the matching official Prometheus release and its checksum index.
It verifies SHA-256 before reading the selected regular `promtool` executable from the archive.
It writes only that executable into a temporary directory and removes the directory after execution.
This component uses the official release executable instead of rebuilding Prometheus and its service-discovery dependencies.
It requires Bun and network access to the official GitHub release assets.
The release selection supports Bun hosts on macOS, Linux, and Windows with x64 or arm64 processors.

Pass native Promtool arguments when checking another fixture:

```sh
bun scripts/tests/promtool/run.ts --version
bun scripts/tests/promtool/run.ts check rules scripts/tests/promtool/alerts/api-errors.rules.yaml
bun scripts/tests/promtool/run.ts test rules scripts/tests/promtool/alerts/api-errors.test.yaml
```

Run the release-integrity regression tests without downloading a release:

```sh
bun run check:promtool-release
```

The rule and tests adapt the examples in `plugins/observability-assets/skills/prometheus-alert-rules/SKILL.md` and `plugins/observability-assets/skills/alert-rule-testing/SKILL.md`.
These files verify the documentation examples.
Do not deploy them as monitoring configuration.
