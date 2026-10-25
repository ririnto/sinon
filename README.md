---
description: >-
  Repository overview for the Sinon Claude Code and Codex plugin marketplace, including structure, marketplace layout, and publishing model.
metadata:
  reference:
    OpenAI skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
    OpenAI plugin packaging:
      url: https://developers.openai.com/plugins/build/plugins.md
    Codex plugin loading:
      version: 0.160.0
      url:
        - https://raw.githubusercontent.com/openai/codex/rust-v0.160.0/codex-rs/utils/plugins/src/plugin_namespace.rs
        - https://raw.githubusercontent.com/openai/codex/rust-v0.160.0/codex-rs/core-plugins/src/loader.rs
    Claude plugin marketplaces:
      url: https://code.claude.com/docs/en/plugins/marketplace-reference.md
    Cursor pstack marketplace:
      url: https://raw.githubusercontent.com/cursor/plugins/main/.claude-plugin/marketplace.json
    Claude plugin components:
      url:
        - https://code.claude.com/docs/en/plugins/manifest-reference.md
        - https://code.claude.com/docs/en/plugins/cli-reference.md
        - https://code.claude.com/docs/en/plugins/troubleshooting.md
    Claude skill frontmatter:
      url: https://code.claude.com/docs/en/skills.md
    Claude subagent frontmatter:
      url: https://code.claude.com/docs/en/sub-agents.md
---

# Sinon

Sinon is a plugin marketplace for Claude Code and Codex.
It publishes curated local plugins and selected external plugins.

Most plugins under `plugins/` are maintained by Sinon.
Herdr is a temporary, upstream-owned marketplace-exposure shim that Sinon does not maintain.
Remove the Herdr shim when its upstream project publishes its own Claude marketplace listing.
Selected external plugins remain maintained in their upstream repositories.
The Claude catalog owns the shared plugin inventory.
The native Codex catalog is generated from that inventory.
Each local plugin that Codex should list provides a native Codex manifest, and a local plugin without one stays Claude-only.
A package with a native manifest that the Claude catalog omits is listed only in the Codex catalog, as `claude-agents` is, because Claude Code reserves the `claude-` name prefix.
Claude Code uses each plugin's marketplace entry as its manifest.

## Repository Structure

- `README.md`: repository overview and marketplace registration guidance.
- `.gitignore`: development ignore rules.
- `.markdownlint-cli2.jsonc`: Markdown lint configuration.
- `.claude-plugin/marketplace.json`: authoritative plugin inventory and Claude marketplace catalog.
- `.agents/plugins/marketplace.json`: generated Codex marketplace catalog.
- `docs/agent-references/`: source and instruction authoring conventions.
- `.claude-plugin/marketplace/`: catalog generation and validation.
- `plugins/<plugin>/tests/`: each plugin's validation suites.
- `package.json`: repository check commands and shared development tools.
- `rules/`: root Markdownlint entry points that re-export the canonical implementations in `plugins/harness/tooling/pnpm/rules/`.
- `plugins/harness/tooling/pnpm/rules/`: canonical Markdown lint rule implementations for the shared profile and repository.
- `.github/`: dependency update configuration and contribution templates.
- `plugins/`: plugins maintained in this repository, except for upstream-owned marketplace-exposure shims.

## Plugin Layout

Each host reads its own plugin metadata:

- `.codex-plugin/plugin.json`: native Codex plugin manifest.
- `.claude-plugin/marketplace.json`: Claude plugin entries and metadata.

Codex discovers shared skills from `skills/` and loads MCP files declared by its manifest.
Root `plugin.json` files are excluded because they shadow native manifests and skip hooks in Codex CLI 0.160.0.
Claude Code uses `.mcp.json` for its MCP servers.
Claude Code discovers shared skills, agents, LSP configuration, and hooks from their default package paths.
Claude-specific agents and LSP configuration retain their host contracts.

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
Portable skills follow OpenAI's task-focused authoring guidance, while Claude marketplace entries retain Claude's component contract.

## Standalone Oxlint Policy

`plugins/no-direct-use-effect.ts` is a self-contained Oxlint module, not an agent marketplace package.
Copy it beside `oxlint.react-effects.config.ts`, preserving the config's `./plugins/` path.
Invoke it with `pnpm exec ultracite check --config oxlint.react-effects.config.ts` using compatible target dependencies.
The configuration combines `react-effects/no-direct-use-effect` with aliased React Hooks and native Oxlint diagnostics.
The [Harness pnpm profile](plugins/harness/docs/tools/pnpm.md#react-effect-policy) provides the same module and installation wiring.
The [upstream mapping](plugins/harness/docs/tools/lint-rule-mapping.md) records retained semantics, replacements, and consumer migration limits.

## Development

Use the [contribution guide](CONTRIBUTING.md) for issues, pull requests, writing rules, and commit evidence.

Use the Node range in `package.json` and the repository's pnpm toolchain.
Run `pnpm install` after dependency changes.
Run `pnpm run check` for the repository checks.
Use the [verification map](docs/verification.md) for component commands, consumer smoke checks, and evidence limits.
The root package commands invoke validation suites owned by their catalog or plugin components.
Use Node.js 24 for development dependencies.
Store shared npm dependency versions in `pnpm-workspace.yaml` catalogs and reference them with `catalog:`.
Follow repository guidance for task-specific checks and package validation.
Run `pnpm run test:promtool` for the [Prometheus documentation fixtures](plugins/observability-assets/tests/promtool/README.md).
That component verifies and runs the official release executable without a separate Go build graph.

## Current Plugins

The following local plugins are listed in both marketplace catalogs, except where a package README names a single host.
For full descriptions, runtime surfaces, and scope notes, see each plugin's own `README.md`.

- [codegraph](./plugins/codegraph/README.md)
- [ddgs](./plugins/ddgs/README.md)
- [document-creator](./plugins/document-creator/README.md)
- [claude-agents](./plugins/claude-agents/README.md)
- [glm-agents](./plugins/glm-agents/README.md)
- [gpt-agents](./plugins/gpt-agents/README.md)
- [harness](./plugins/harness/README.md)
- [herdr](./plugins/herdr/README.md): an upstream-owned, temporary marketplace-exposure shim that Sinon does not maintain.
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

- [codex-app-server](https://github.com/ririnto/codex-app-server): the Sinon catalog follows the upstream `main` branch.
  The upstream repository owns the MCP runtime, thread-control skill, monitor, checks, and release version.
  Install it as `codex-app-server@sinon` in either host.
- [workgraph](https://github.com/ririnto/workgraph): the Sinon catalog follows the upstream `main` branch.
  The upstream repository owns the runtime, Skills, and version.

## Install pstack from the official marketplace

Install pstack through the official `cursor/plugins` marketplace in Claude Code:

```sh
claude plugin marketplace add cursor/plugins
claude plugin install pstack@cursor-plugins
```

The upstream marketplace owns pstack packaging, compatibility, and updates.

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

## Registering This Marketplace in Codex

Add the repository marketplace and install a plugin with the supported Codex CLI:

```sh
codex plugin marketplace add ririnto/sinon --ref main
codex plugin list --available --marketplace sinon
codex plugin add ddgs@sinon
```

For a local checkout, use `codex plugin marketplace add /path/to/sinon`.
The repository catalog at `.agents/plugins/marketplace.json` exposes the Claude catalog entries that have a native Codex manifest, plus Codex-only packages.
Its local source paths are relative to the repository root.
External Workgraph continues to follow its upstream `main` branch.

Install plugins through the Codex plugin browser when using the desktop app.
Start a new session after installation and trust hooks when the host requests it.
CodeGraph provides separate Codex startup hooks and Claude startup and `EnterWorktree` hooks.
DDGS and CodeGraph prepare their upstream tools on demand with native package caches.
See each package README for runtime requirements and host-specific capabilities.

## Maintaining The Catalogs

Edit `.claude-plugin/marketplace.json` to add, remove, or change a catalog entry.
Keep each local package's Codex identity aligned with its Claude marketplace entry.
The marketplace catalog owns its generator and regression suite under `.claude-plugin/marketplace/`.
Generate the native Codex view with `pnpm run marketplace:generate`.
External `git-subdir` sources preserve their URL, subdirectory, and Git selector in the generated view.
Commit the generated catalog with its source changes.
The repository checks reject stale catalogs and invalid local package mappings.

## License

The repository root and plugins whose manifests declare `MIT` use the [MIT License](./LICENSE).
The `claude-agents`, `glm-agents`, and `gpt-agents` packages also include a local copy of that notice.

The following plugins use their local canonical Apache-2.0 license:

- [harness](./plugins/harness/LICENSE)
- [herdr](./plugins/herdr/LICENSE)
- [spec-driven-development](./plugins/spec-driven-development/LICENSE)
- [workspace-workflow](./plugins/workspace-workflow/LICENSE)
