---
description: >-
  Repository overview for the Sinon Claude Code plugin marketplace, including structure, marketplace layout, and publishing model.
metadata:
  reference:
    OpenAI skills:
      url: https://developers.openai.com/codex/skills
    OpenAI plugin packaging:
      url: https://developers.openai.com/plugins/build/plugins
    Claude plugin marketplaces:
      url: https://code.claude.com/docs/en/plugins/marketplace-reference
    Claude plugin components:
      url:
        - https://code.claude.com/docs/en/plugins/cli-reference
        - https://code.claude.com/docs/en/plugins/troubleshooting
---

# Sinon

Sinon is a Claude Code plugin marketplace repository.
It publishes curated local plugins and selected external plugins.

Sinon-maintained plugins live under `plugins/`.
Selected external plugins are registered in the Claude marketplace catalog and remain maintained in their upstream repositories.
The Claude marketplace catalog lives at the repository root.
The codegraph plugin also provides a portable Agent Plugins manifest and Codex startup hooks.

## Repository Structure

- `README.md`: repository overview and marketplace registration guidance.
- `.gitignore`: development ignore rules.
- `.markdownlint-cli2.jsonc`: Markdown lint configuration.
- `.claude-plugin/marketplace.json`: Claude marketplace catalog.
- `docs/agent-references/`: source and instruction authoring conventions.
- `scripts/`: repository checks and native example fixtures.
- `rules/`: repository Markdown lint rules.
- `.github/`: dependency update configuration.
- `plugins/`: plugins maintained in this repository.

## Plugin Layout

Each Sinon-maintained plugin directory may expose a Claude Code manifest from the same plugin root:

- `.claude-plugin/plugin.json`: Claude plugin manifest.

The codegraph package also contains portable `plugin.json` and `mcp.json` files for Codex.

Optional assets live beside the manifest at the plugin root.
Common plugin assets include:

- `README.md`.
- `skills/`.
- `agents/`.
- `hooks/`.
- `.mcp.json`.
- `settings.json`.
- Executable `bin/`.

Use each plugin's `README.md` for its runtime surfaces and scope.
Portable skills follow OpenAI's task-focused authoring guidance, while Claude manifests retain Claude's component contract.

## Development

Use the Node range in `package.json` and the repository's pnpm toolchain.
Run `pnpm install` after dependency changes.
Run `pnpm run check` for the repository checks.
Use Node.js 24 for development dependencies.
Store shared npm dependency versions in `pnpm-workspace.yaml` catalogs and reference them with `catalog:`.
Use [repository instructions](AGENTS.md) for task-specific checks and package validation.
Run `pnpm run test:promtool` for the [Prometheus documentation fixtures](scripts/tests/promtool/README.md).
That component verifies and runs the official release executable without a separate Go build graph.

## Current Plugins

The following plugins are maintained in this repository and may be published to the Claude marketplace catalog.
For full descriptions, runtime surfaces, and scope notes, see each plugin's own `README.md`.

- [codegraph](./plugins/codegraph/README.md)
- [document-creator](./plugins/document-creator/README.md)
- [harness](./plugins/harness/README.md)
- [java](./plugins/java/README.md)
- [jvm](./plugins/jvm/README.md)
- [kotlin](./plugins/kotlin/README.md)
- [netty](./plugins/netty/README.md)
- [observability-assets](./plugins/observability-assets/README.md)
- [reactor](./plugins/reactor/README.md)
- [spec-driven-development](./plugins/spec-driven-development/README.md)
- [spring](./plugins/spring/README.md)
- [workspace-workflow](./plugins/workspace-workflow/README.md)

## External Plugins

Sinon selects external plugins through its marketplace catalog.

- [workgraph](https://github.com/ririnto/workgraph): the Sinon catalog follows the upstream `main` branch.
  The upstream repository owns the runtime, Skills, and version.

## Registering This Marketplace in Claude Code

Claude Code supports registering marketplaces from GitHub repositories, generic git URLs, direct URLs to `marketplace.json`, and local paths.

For this repository, use a GitHub repository, git URL, or local path.
Sinon uses relative sources such as `./plugins/java` inside `.claude-plugin/marketplace.json`, which require the plugin files from a checkout.
A direct HTTP URL fetches only the catalog and cannot resolve these relative plugin sources.

The Claude marketplace catalog for this repository is:

- `.claude-plugin/marketplace.json`

### Interactive registration

Register this marketplace from a local checkout:

```sh
claude plugin marketplace add /path/to/sinon
```

Register this marketplace from GitHub:

```sh
claude plugin marketplace add ririnto/sinon
```

Register this marketplace from a generic git URL:

```sh
claude plugin marketplace add https://github.com/ririnto/sinon.git
```

After Claude Code registers the `sinon` marketplace, install a plugin from it with:

```sh
claude plugin install <plugin>@sinon
```

Examples:

```sh
claude plugin install document-creator@sinon
claude plugin install codegraph@sinon
claude plugin install harness@sinon
claude plugin install workgraph@sinon
claude plugin install java@sinon
claude plugin install jvm@sinon
claude plugin install kotlin@sinon
claude plugin install netty@sinon
claude plugin install observability-assets@sinon
claude plugin install reactor@sinon
claude plugin install spec-driven-development@sinon
claude plugin install spring@sinon
claude plugin install workspace-workflow@sinon
```

### `~/.claude/settings.json`

You can also preconfigure the marketplace in `~/.claude/settings.json`:

```json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "extraKnownMarketplaces": {
    "sinon": {
      "source": {
        "source": "github",
        "repo": "ririnto/sinon"
      }
    }
  }
}
```

To enable a plugin by default, add it to `enabledPlugins`:

```json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "extraKnownMarketplaces": {
    "sinon": {
      "source": {
        "source": "github",
        "repo": "ririnto/sinon"
      }
    }
  },
  "enabledPlugins": {
    "java@sinon": true
  }
}
```

For a local checkout, use a directory source instead:

```json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "extraKnownMarketplaces": {
    "sinon": {
      "source": {
        "source": "directory",
        "path": "/path/to/sinon"
      }
    }
  }
}
```

If you are working from a local checkout instead of a registered marketplace, you can also load a plugin directly from its plugin root:

```sh
claude --plugin-dir /path/to/sinon/plugins/java
```

### CodeGraph in Codex

Codex can discover the existing `.claude-plugin/marketplace.json` as a compatible repository marketplace.
Install `codegraph` from Sinon through the plugin browser, then start a new session and trust its startup hook.
The package's portable manifest selects the Codex hook configuration.
See the [CodeGraph package](./plugins/codegraph/README.md) for runtime requirements and host behavior.

## License

The repository root and plugins whose manifests declare `MIT` use the [MIT License](./LICENSE).

The following plugins use their local canonical Apache-2.0 license:

- [harness](./plugins/harness/LICENSE)
- [spec-driven-development](./plugins/spec-driven-development/LICENSE)
- [workspace-workflow](./plugins/workspace-workflow/LICENSE)
