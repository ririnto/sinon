---
title: Sinon
description: >-
  Repository overview for the Sinon universal AI plugin marketplace, including structure, marketplace layout, and publishing model.
---

Sinon is a universal AI plugin marketplace repository.

This repository uses the official marketplace paths for Claude Code and Codex, with plugins stored under `plugins/`.

## Purpose

- Publish curated marketplace catalogs from a Git repository.
- Represent plugins for multiple AI runtimes without splitting plugin source trees.
- Store installable plugin packages in a consistent directory layout.
- Keep runtime-specific marketplace metadata and plugin manifests aligned.

## Repository Structure

- `README.md`: repository overview and contribution context.
- `.gitignore`: development ignore rules.
- `.markdownlint.jsonc`: Markdown lint configuration.
- `.claude-plugin/marketplace.json`: Claude marketplace catalog.
- `.agents/plugins/marketplace.json`: Codex marketplace catalog.
- `plugins/`: plugins maintained in this repository.

## Marketplace Layout

Plugins are stored under `plugins/`.

Each plugin directory can expose one or more runtime manifests from the same plugin root.

- `.claude-plugin/plugin.json`: Claude plugin manifest.
- `.codex-plugin/plugin.json`: Codex plugin manifest.
Optional plugin assets such as `README.md`, `.mcp.json`, `.app.json`, `commands/`, `agents/`, `skills/`, and `assets/` live beside those manifests at the plugin root.

The repository keeps separate official marketplace catalogs per runtime.

## Current Plugins

- `plugins/git-workflow`: Git workflow plugin with practical guidance for commit readiness, Conventional Commit drafting, staged-change hygiene, and template-aware GitHub pull request or GitLab merge request body drafting.
- `plugins/java`: Java development plugin with practical skills for syntax boundaries, language design, testing workflows, dependency decisions, performance analysis, and JDTLS-assisted editing.
- `plugins/jdk`: JDK development assistant with shared skills for tooling workflows, runtime diagnostics, and garbage-collection guidance.
- `plugins/kotlin`: Kotlin development plugin with practical skills for idiomatic language design, coroutines and Flow decisions, Kotlin testing workflows, and kotlin-lsp-assisted editing.
- `plugins/spring`: Spring development plugin with practical skills for Spring Boot, Web, Data, transactions, observability, Batch, scheduling runtime control, Quartz job/trigger operations, Integration, Cloud, and Kafka workflows.
- `plugins/observability-assets`: Prometheus and Grafana plugin with practical skills for alert-rule design, recording-rule support, promtool validation, dashboard JSON authoring, and Grafana mixin workflows for version-controlled observability assets.
- `plugins/reactor`: Project Reactor plugin with practical skills for Flux and Mono composition, scheduler selection, Sinks and ConnectableFlux hot-source design, and reactor-test workflows with StepVerifier, TestPublisher, PublisherProbe, and virtual time.
- `plugins/netty`: Netty and Reactor Netty plugin with practical skills for high-performance network applications, bootstrap and pipeline design, ByteBuf and codec handling, and reactive HTTP/TCP/UDP workflows with Reactor Netty.

## Publishing Model

`.claude-plugin/marketplace.json` is the Claude marketplace catalog.

`.agents/plugins/marketplace.json` is the Codex marketplace catalog.

Individual plugin directories remain the source of truth for plugin-specific runtime manifests and bundled assets.

Bundled upstream plugins may support only a subset of runtimes. In some cases, this repository may add minimal runtime metadata such as `.codex-plugin/` while leaving the upstream plugin content otherwise intact.

## Registering This Marketplace in Claude Code

Claude Code supports registering marketplaces from GitHub repositories, generic git URLs, direct URLs to `marketplace.json`, and local paths.

For this repository, use a GitHub repository, git URL, or local path. Sinon currently uses relative plugin sources such as `./plugins/java` inside `.claude-plugin/marketplace.json`, so a direct HTTP URL to the catalog file is not a safe distribution path for this marketplace.

The Claude marketplace catalog for this repository is:

- `.claude-plugin/marketplace.json`

### Interactive registration

Register this marketplace from a local checkout:

```bash
/plugin marketplace add /path/to/sinon
```

Register this marketplace from GitHub:

```bash
/plugin marketplace add ririnto/sinon
```

Register this marketplace from a generic git URL:

```bash
/plugin marketplace add https://github.com/ririnto/sinon.git
```

After Claude Code registers the `sinon` marketplace, install a plugin from it with:

```bash
/plugin install <plugin>@sinon
```

Examples:

```bash
/plugin install git-workflow@sinon
/plugin install java@sinon
/plugin install jdk@sinon
/plugin install kotlin@sinon
/plugin install spring@sinon
/plugin install observability-assets@sinon
/plugin install reactor@sinon
/plugin install netty@sinon
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

```bash
cc --plugin-dir /path/to/sinon/plugins/java
```

## License

This repository is distributed under the MIT License. See [LICENSE](./LICENSE).
