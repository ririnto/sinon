---
metadata:
  reference:
    Node.js:
      version: 18
      url:
        - https://nodejs.org/docs/latest-v18.x/api/crypto.md
        - https://nodejs.org/docs/latest-v18.x/api/fs.md
        - https://nodejs.org/docs/latest-v18.x/api/child_process.md
    node-tar:
      version: 7.5.22
      url: https://raw.githubusercontent.com/isaacs/node-tar/v7.5.22/README.md
    Prometheus:
      version: 3.15.0
      url:
        - https://github.com/prometheus/prometheus/releases/tag/v3.15.0
        - https://github.com/prometheus/prometheus/releases/download/v3.15.0/sha256sums.txt
        - https://raw.githubusercontent.com/prometheus/prometheus/v3.15.0/docs/configuration/unit_testing_rules.md
---

# Prometheus Rule Tests

Run from the repository root:

```sh
pnpm run test:promtool
```

The runner checks the rule file and then runs its unit test fixture.
`package.json` owns the release pin in `config.promtoolVersion`.
When updating the pin, use the latest compatible stable official release and rerun the native fixture checks.
The runner downloads the matching official Prometheus release and its checksum index.
It verifies SHA-256 before reading the selected regular `promtool` executable from the archive.
It rejects missing, duplicate, and nonregular executable entries, and invalid archives.
It reads the selected executable as binary bytes without extracting archive paths to disk.
It writes only that executable into a temporary directory and removes the directory after execution.
This component uses the official release executable instead of rebuilding Prometheus and its service-discovery dependencies.
It requires a supported Node.js release, pnpm, and network access to the official GitHub release assets.
The release selection supports Node.js hosts on macOS, Linux, and Windows with x64 or arm64 processors.

Pass native Promtool arguments when checking another fixture:

```sh
pnpm run test:promtool --version
pnpm run test:promtool check rules scripts/tests/promtool/alerts/api-errors.rules.yaml
pnpm run test:promtool test rules scripts/tests/promtool/alerts/api-errors.test.yaml
```

Run the release-integrity regression tests without downloading a release:

```sh
pnpm run check:promtool-release
```

The rule and tests adapt the examples in `plugins/observability-assets/skills/prometheus-alert-rules/SKILL.md` and `plugins/observability-assets/skills/alert-rule-testing/SKILL.md`.
These files verify the documentation examples.
Do not deploy them as monitoring configuration.
