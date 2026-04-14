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

- `plugins/java`: Java language plugin for syntax boundaries, API design, testing, dependency decisions, performance review, and JDTLS-assisted editing.
- `plugins/jdk`: JDK tooling plugin for runtime diagnostics, garbage-collection guidance, and standard JDK command workflows.
- `plugins/kotlin`: Kotlin plugin for language patterns, coroutine and Flow design, Kotlin testing, and kotlin-lsp-assisted editing.
- `plugins/spring`: Spring plugin for Boot, Web, Data, transactions, observability, scheduling, messaging, batch, and cloud workflows.

## Publishing Model

`.claude-plugin/marketplace.json` is the Claude marketplace catalog.

`.agents/plugins/marketplace.json` is the Codex marketplace catalog.

Individual plugin directories remain the source of truth for plugin-specific runtime manifests and bundled assets.

Bundled upstream plugins may support only a subset of runtimes. In some cases, this repository may add minimal runtime metadata such as `.codex-plugin/` while leaving the upstream plugin content otherwise intact.

## License

This repository is distributed under the MIT License. See [LICENSE](./LICENSE).
