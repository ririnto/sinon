---
description: >-
  Repository overview for the Sinon Claude Code plugin marketplace, including structure, marketplace layout, and publishing model.
---

# Sinon

Sinon is a Claude Code plugin marketplace repository.
It publishes curated plugins from a single source tree.

Plugins live under `plugins/`.
The Claude marketplace catalog lives at the repository root.
Always-on repository rules are in `AGENTS.md`.

## Repository Structure

- `AGENTS.md`: repository invariants, build and check commands, and change discipline.
- `CLAUDE.md`: Claude Code pointer that imports `AGENTS.md`.
- `README.md`: repository overview and marketplace registration guidance.
- `.gitignore`: development ignore rules.
- `.markdownlint-cli2.jsonc`: Markdown lint configuration.
- `.claude-plugin/marketplace.json`: Claude marketplace catalog.
- `plugins/`: plugins maintained in this repository.

## Plugin Layout

Each plugin directory may expose a Claude Code manifest from the same plugin root:

- `.claude-plugin/plugin.json`: Claude plugin manifest.

Optional assets live beside the manifest at the plugin root.
Common plugin assets include:

- `README.md`.
- `agents/`.
- `skills/`.
- `hooks/`.
- `.mcp.json`.
- `settings.json`.
- Executable `bin/`.

Plugin-specific details belong in each plugin's own `README.md`, not in this root document.

## Current Plugins

The following plugins are maintained in this repository and may be published to the Claude marketplace catalog.
For full descriptions, runtime surfaces, and scope notes, see each plugin's own `README.md`.

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

## Publishing Model

The repository maintains one marketplace catalog:

- `.claude-plugin/marketplace.json` for Claude Code.

Individual plugin directories remain the source of truth for plugin-specific runtime manifests and bundled assets.
The marketplace catalog lists plugin roots that ship the Claude manifest.

## Registering This Marketplace in Claude Code

Claude Code supports registering marketplaces from GitHub repositories, generic git URLs,
direct URLs to `marketplace.json`, and local paths.

For this repository, use a GitHub repository, git URL, or local path.
Sinon uses relative plugin sources such as `./plugins/java` inside
`.claude-plugin/marketplace.json`, so a direct HTTP URL to the catalog file is not a safe
distribution path for this marketplace.

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
claude plugin install harness@sinon
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

## License

The repository root and plugins whose manifests declare `MIT` use the [MIT License](./LICENSE).

The following plugins use their local canonical Apache-2.0 license:

- [harness](./plugins/harness/LICENSE), with attribution notes in [THIRD_PARTY_NOTICES.md](./plugins/harness/THIRD_PARTY_NOTICES.md)
- [spec-driven-development](./plugins/spec-driven-development/LICENSE)
- [workspace-workflow](./plugins/workspace-workflow/LICENSE)
