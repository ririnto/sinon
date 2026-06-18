---
description: >-
  Repository overview for the Sinon Claude Code plugin marketplace, including structure, marketplace layout, and publishing model.
---

# Sinon

Sinon is a Claude Code plugin marketplace repository.
It publishes curated plugins from a single source tree.

Plugins live under `plugins/`.
The Claude marketplace catalog lives at the repository root.
Stable repository rules, layout policies, and skill-authoring contracts are in `AGENTS.md`.

## Purpose

- Publish a curated Claude Code marketplace catalog from a Git repository.
- Store installable plugin packages in a consistent directory layout.
- Keep marketplace metadata and plugin manifests aligned.
- Keep generic Agent Skills mirrored through `.claude/skills/` and `.agents/skills/` without treating those paths as plugin marketplace catalogs.

## Repository Structure

- `AGENTS.md`: stable repository rules, skill-authoring contracts, and layout policies.
- `CLAUDE.md`: Claude Code pointer that imports `AGENTS.md`.
- `README.md`: repository overview and marketplace registration guidance.
- `.gitignore`: development ignore rules.
- `.markdownlint-cli2.jsonc`: Markdown lint configuration.
- `.claude-plugin/marketplace.json`: Claude marketplace catalog.
- `.claude/skills/`, `.claude/agents/`, `.claude/commands/`, and `.agents`: mirrored symlink entries that resolve to the same `.claude` tree, which maps to `plugins/agent-capability-kit/{skills,agents,commands}/`.
- `plugins/`: plugins maintained in this repository.

## Plugin Layout

Each plugin directory may expose a Claude Code manifest from the same plugin root:

- `.claude-plugin/plugin.json`: Claude plugin manifest.

Optional assets such as `README.md`, `commands/`, `agents/`, `skills/`, `hooks/`, `.mcp.json`, and `settings.json` live beside the manifest at the plugin root.
Plugin-specific details belong in each plugin's own `README.md`, not in this root document.

## Current Plugins

The following plugins are maintained in this repository and may be published to the Claude marketplace catalog.
For full descriptions, runtime surfaces, and scope notes, see each plugin's own `README.md`.

- `plugins/agent-capability-kit`: Authoring kit for Claude Code plugins, agents, commands/prompts, and cross-platform Agent Skills.
- `plugins/document-creator`: Authoring skills for AI-consumable engineering documents, including `SPEC.md` creation.
- `plugins/harness`: Repository harness plugin for installing, validating, and evolving agent instructions, docs structure, project agents, project skills, templates, stack validators, CI snippets, and opt-in Git hook templates.
- `plugins/java`: Java development plugin with practical skills for syntax boundaries, language design, testing workflows, dependency decisions, performance analysis, and JDTLS-assisted editing.
- `plugins/jvm`: JVM development assistant with shared skills for tooling workflows, runtime diagnostics, and garbage-collection guidance.
- `plugins/kotlin`: Kotlin development plugin with practical skills for idiomatic language design, coroutines and Flow decisions, Kotlin testing workflows, and kotlin-lsp-assisted editing.
- `plugins/netty`: Netty and Reactor Netty plugin with practical skills for high-performance network applications, bootstrap and pipeline design, ByteBuf and codec handling, and reactive HTTP/TCP/UDP workflows with Reactor Netty.
- `plugins/observability-assets`: Prometheus and Grafana plugin with practical skills for alert-rule design, recording-rule support, promtool validation, dashboard JSON authoring, and Grafana mixin workflows for version-controlled observability assets.
- `plugins/reactor`: Project Reactor plugin with practical skills for Flux and Mono composition, scheduler selection, Sinks and ConnectableFlux hot-source design, and reactor-test workflows with StepVerifier, TestPublisher, PublisherProbe, and virtual time.
- `plugins/spec-driven-development`: Spec-first workflow: research unknowns, write abstract requirements in `SPEC.md`, get approval, implement, verify completeness.
- `plugins/spring`: Spring development plugin with practical skills for Spring Boot, Web, Data, transactions, observability, Batch, Integration, Cloud, and Kafka workflows.
- `plugins/workspace-workflow`: Coordinate workspace and Git workflow across worktree management, working-tree hygiene, merge and rebase strategies, commit conventions, and PR/MR composition.

## Publishing Model

The repository maintains one marketplace catalog:

- `.claude-plugin/marketplace.json` for Claude Code.

Individual plugin directories remain the source of truth for plugin-specific runtime manifests and bundled assets.
The marketplace catalog lists plugin roots that ship the Claude manifest.

## Registering This Marketplace in Claude Code

Claude Code supports registering marketplaces from GitHub repositories, generic git URLs, direct URLs to `marketplace.json`, and local paths.

For this repository, use a GitHub repository, git URL, or local path.
Sinon currently uses relative plugin sources such as `./plugins/java` inside `.claude-plugin/marketplace.json`, so a direct HTTP URL to the catalog file is not a safe distribution path for this marketplace.

The Claude marketplace catalog for this repository is:

- `.claude-plugin/marketplace.json`

### Interactive registration

Register this marketplace from a local checkout:

```sh
/plugin marketplace add /path/to/sinon
```

Register this marketplace from GitHub:

```sh
/plugin marketplace add ririnto/sinon
```

Register this marketplace from a generic git URL:

```sh
/plugin marketplace add https://github.com/ririnto/sinon.git
```

After Claude Code registers the `sinon` marketplace, install a plugin from it with:

```sh
/plugin install <plugin>@sinon
```

Examples:

```sh
/plugin install agent-capability-kit@sinon
/plugin install document-creator@sinon
/plugin install harness@sinon
/plugin install java@sinon
/plugin install jvm@sinon
/plugin install kotlin@sinon
/plugin install netty@sinon
/plugin install observability-assets@sinon
/plugin install reactor@sinon
/plugin install spec-driven-development@sinon
/plugin install spring@sinon
/plugin install workspace-workflow@sinon
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

This repository is distributed under the MIT License.
See [LICENSE](./LICENSE).
The harness plugin is distributed under Apache-2.0 and includes attribution notes under [plugins/harness/THIRD_PARTY_NOTICES.md](./plugins/harness/THIRD_PARTY_NOTICES.md).
