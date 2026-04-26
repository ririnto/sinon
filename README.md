---
title: Sinon
description: >-
  Repository overview for the Sinon universal AI plugin marketplace, including structure, marketplace layout, and publishing model.
---

Sinon is a universal AI plugin marketplace repository. It publishes curated plugins for Claude Code and Codex from a single source tree.

Plugins live under `plugins/`. Runtime-specific marketplace catalogs live at the repository root. Stable repository rules, layout policies, and skill-authoring contracts are in `CLAUDE.md`.

## Purpose

- Publish curated marketplace catalogs from a Git repository.
- Represent plugins for multiple AI runtimes without splitting plugin source trees.
- Store installable plugin packages in a consistent directory layout.
- Keep runtime-specific marketplace metadata and plugin manifests aligned.

## Repository Structure

- `CLAUDE.md`: stable repository rules, skill-authoring contracts, and layout policies. `AGENTS.md` is a symlink to the same file.
- `README.md`: repository overview and marketplace registration guidance.
- `.gitignore`: development ignore rules.
- `.markdownlint.jsonc`: Markdown lint configuration.
- `.claude-plugin/marketplace.json`: Claude marketplace catalog.
- `.agents/plugins/marketplace.json`: Codex marketplace catalog.
- `.claude/skills/` and `.agents/skills/`: mirrored symlink entries that resolve to `plugins/agent-capability-kit/skills/`.
- `plugins/`: plugins maintained in this repository.

## Plugin Layout

Each plugin directory exposes one or more runtime manifests from the same plugin root:

- `.claude-plugin/plugin.json`: Claude plugin manifest.
- `.codex-plugin/plugin.json`: Codex plugin manifest.

Optional assets such as `README.md`, `commands/`, `agents/`, `skills/`, `hooks/`, `.mcp.json`, and `settings.json` live beside those manifests at the plugin root. Plugin-specific details belong in each plugin's own `README.md`, not in this root document.

## Current Plugins

The following plugins are listed in both `.claude-plugin/marketplace.json` and `.agents/plugins/marketplace.json`. For full descriptions, runtime surfaces, and scope notes, see each plugin's own `README.md`.

- `plugins/agent-capability-kit`: Authoring kit for Claude Code plugins, agents, commands, and cross-platform Agent Skills.
- `plugins/git-workflow`: Git workflow plugin with practical guidance for commit readiness, Conventional Commit drafting, staged-change hygiene, and template-aware GitHub pull request or GitLab merge request body drafting.
- `plugins/harness-engineering`: Agent-first repository design with progressive disclosure, architecture enforcement, and entropy management for AI agent development.
- `plugins/java`: Java development plugin with practical skills for syntax boundaries, language design, testing workflows, dependency decisions, performance analysis, and JDTLS-assisted editing.
- `plugins/jvm`: JVM development assistant with shared skills for tooling workflows, runtime diagnostics, and garbage-collection guidance.
- `plugins/kotlin`: Kotlin development plugin with practical skills for idiomatic language design, coroutines and Flow decisions, Kotlin testing workflows, and kotlin-lsp-assisted editing.
- `plugins/netty`: Netty and Reactor Netty plugin with practical skills for high-performance network applications, bootstrap and pipeline design, ByteBuf and codec handling, and reactive HTTP/TCP/UDP workflows with Reactor Netty.
- `plugins/observability-assets`: Prometheus and Grafana plugin with practical skills for alert-rule design, recording-rule support, promtool validation, dashboard JSON authoring, and Grafana mixin workflows for version-controlled observability assets.
- `plugins/reactor`: Project Reactor plugin with practical skills for Flux and Mono composition, scheduler selection, Sinks and ConnectableFlux hot-source design, and reactor-test workflows with StepVerifier, TestPublisher, PublisherProbe, and virtual time.
- `plugins/spec-driven-development`: Spec-first workflow: research unknowns, write abstract requirements in SPEC.md, get approval, implement, verify completeness.
- `plugins/spring`: Spring development plugin with practical skills for Spring Boot, Web, Data, transactions, observability, Batch, Integration, Cloud, and Kafka workflows.

## Publishing Model

The repository maintains one marketplace catalog per runtime:

- `.claude-plugin/marketplace.json` for Claude Code.
- `.agents/plugins/marketplace.json` for Codex.

Individual plugin directories remain the source of truth for plugin-specific runtime manifests and bundled assets. Bundled upstream plugins may support only a subset of runtimes; in those cases, the repository adds minimal runtime metadata without altering upstream content.

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
/plugin install agent-capability-kit@sinon
/plugin install git-workflow@sinon
/plugin install harness-engineering@sinon
/plugin install java@sinon
/plugin install jvm@sinon
/plugin install kotlin@sinon
/plugin install netty@sinon
/plugin install observability-assets@sinon
/plugin install reactor@sinon
/plugin install spec-driven-development@sinon
/plugin install spring@sinon
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
claude --plugin-dir /path/to/sinon/plugins/java
```

## License

This repository is distributed under the MIT License. See [LICENSE](./LICENSE).
