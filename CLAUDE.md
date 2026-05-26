---
title: Sinon Project Rules
description: >-
  Stable repository rules for plugin packaging and skill authoring in the Sinon marketplace.
---

Sinon is a marketplace repository for Claude Code plugins and Agent Skills. These rules govern repository layout, skill authoring, and documentation posture. Normative keywords (MUST, MUST NOT, SHOULD, SHOULD NOT, MAY) follow BCP 14. All repository-level and agent-facing rules documents MUST be written in English.

The agentskills.io loading model is the top-level governing basis for skill structure and progressive disclosure. Claude Code plugin packaging rules extend that model but do not override it.

## Canonical files and symlinks

- `CLAUDE.md` is the canonical root rules document. `AGENTS.md` is a symlink to it; treat them as one document, not parallel copies.
- `plugins/agent-capability-kit/` is the canonical repository source for the shared authoring skills, agents, and commands in this repository.
- `.claude/skills/`, `.claude/agents/`, and `.claude/commands/` are directory symlinks that resolve to `plugins/agent-capability-kit/{skills,agents,commands}/` respectively.
- `.agents` is a symlink to `.claude`; treat all paths that resolve to the same target as one inventory, not separate sources.
- Root-level documentation MUST describe repository-wide structure and rules, not fast-changing plugin details.

## Versioning

- Plugins MUST NOT carry a version field. Versions are managed at the marketplace level, not within individual plugins.

## Repository layout

- Plugins MUST live under `plugins/`.
- Each plugin MAY expose a Claude Code runtime manifest from its plugin root.
- Runtime marketplace metadata MUST stay aligned with the plugin content it publishes.
- Runtime catalogs MUST publish only plugin roots that include the matching runtime manifest.
- Each plugin root MUST ship a `README.md` that describes the plugin's purpose, included skills, agents, commands, runtime model, layout, and scope notes.

## Plugin manifests

Plugin roots MAY ship `.claude-plugin/plugin.json` for Claude Code publication.

Claude Code plugin manifest rules:

- `.claude-plugin/plugin.json` MUST include the `$schema` field `"https://anthropic.com/claude-code/plugin.schema.json"`.
- `author` MUST use the object form (for example, `"author": { "name": "ririnto" }`).
- Every declared path inside `plugin.json` MUST begin with `./`.
- Directory-typed fields, when present, MUST use the trailing-slash directory form. `skills` MUST be exactly `"./skills/"` and `commands` MUST be exactly `"./commands/"`. Array-of-paths form MUST NOT be used for either field.
- File-typed fields, when present, MUST point to the canonical filename at the plugin root: `hooks` MUST be exactly `"./hooks/hooks.json"`, `mcpServers` MUST be exactly `"./.mcp.json"`, `lspServers` MUST be exactly `"./.lsp.json"`, and `settings` MUST be exactly `"./settings.json"`.
- The manifest and the plugin-root filesystem MUST stay bidirectionally consistent. When a file-typed manifest field is declared, the corresponding plugin-root file MUST exist. When `hooks/hooks.json`, `.mcp.json`, `.lsp.json`, or `settings.json` exists at the plugin root, the manifest SHOULD declare the matching field so the runtime publishes the surface.
- `agents` MUST NOT appear in the manifest because the Claude Code manifest schema does not support an `agents` key. When a plugin ships agents, keep the `agents/` directory at the plugin root and describe that runtime surface in the plugin README instead of declaring an `agents` manifest key.
- `version` MUST NOT appear in any plugin manifest.
- `.claude-plugin/plugin.json` MUST NOT include an `interface` block.

Plugin structure rules:

- Plugins with commands MUST ship a `commands/` directory at the plugin root with one `.md` file per command; commands are identified by file basename and NEED NOT declare a `name` frontmatter field.
- Plugins with agents MUST ship an `agents/` directory at the plugin root with one `.md` file per agent whose frontmatter `name` matches the file basename exactly. Both the filename stem and the `name` field MUST use kebab-case.

## Authoring Agent Skills

When the task is to create, edit, review, refactor, validate, or package an Agent Skill for this repository, you MUST load the local `skill-authoring` skill from `plugins/agent-capability-kit/skills/skill-authoring/`. The paths `.claude/skills/skill-authoring/` and `.agents/skills/skill-authoring/` resolve to the same source content through the directory-level symlinks (`.agents` → `.claude` → per-surface symlinks into `plugins/agent-capability-kit/`).

Progressive disclosure applies at three levels:

1. Skill `description` metadata is the activation trigger. It MUST open with a capability statement written as an imperative clause that names what the skill does (for example, "Design...", "Write...", "Build...", "Author...", "Triage...", "Integrate..."). A trigger clause SHOULD follow only when it adds vocabulary absent from the capability statement (alternative artifact names like RFC/SRS, domain-specific terms like normative, timing/context cues like before implementation, alternative naming users commonly use). Each item within the trigger clause MUST contribute at least one new signal; verb-synonym duplication of the capability verb is PROHIBITED. Starting the description with the trigger clause alone, without an opening capability statement, is PROHIBITED.
2. `SKILL.md` is the common-path entrypoint loaded at activation and MUST be self-sufficient for the ordinary task.
3. `references/`, `assets/`, and `scripts/` hold on-demand additive depth and MUST NOT be treated as always-loaded context.

A skill SHOULD cover one coherent unit of work. When sibling skills share a common path and differ only by host, vendor, or platform, they SHOULD be merged and the deltas SHOULD move to focused references.

## `SKILL.md` contract

A skill MUST remain usable when installed by itself and MUST NOT require another skill as a prerequisite or routing handoff. A single `SKILL.md` MAY cover multiple hosts or platforms when the user job is the same and the common path stays in `SKILL.md`. Plugin-level inventories MAY list bundled skills, but each skill entrypoint MUST remain self-sufficient.

`SKILL.md` MUST contain:

- A `name` field exactly matching the skill directory basename.
- The activation surface, common-case workflow, decision points, and first safe commands.
- Representative templates, copy-adaptable examples, invariants, pitfalls, and scope boundaries.
- Primary authoring conventions users apply during ordinary use of the skill.
- The shared workflow and first safe commands when the skill spans multiple hosts or platforms.
- Format-critical output shapes.
- Brief pointers to `references/` indexed by concrete blocker or job.

`SKILL.md` MUST NOT:

- Move common-case guidance to `references/` solely to shrink `SKILL.md`.
- Describe adjacent-domain exclusions as "jump to skill X"; state them in domain terms.
- Degrade into a generic essay or background article.

`SKILL.md` SHOULD favor direct, imperative guidance over tutorial narration and SHOULD keep a shallow directory structure so agents can discover material quickly. Skill documentation MUST be self-contained enough to enable productive offline development; it MUST NOT assume the reader has live internet access to external documentation, registries, or remote services during ordinary use.

## `references/` contract

`references/` MUST contain additive depth only: extended examples, host-specific template paths and command variants, operational caveats, version boundaries, and edge-case decision material. Each reference file MUST be a purpose-complete unit that states its purpose and the condition for opening it, and MUST stand alone for one specific blocker or job. `references/` MAY assume the reader has already activated `SKILL.md`.

`references/` MUST NOT:

- Hold material required for the common case or primary purpose.
- Repeat canonical templates, workflow steps, invariants, or pitfalls owned by `SKILL.md`.
- Act as duplicate standalone skill files.

`references/` SHOULD avoid chains; references commonly read together for the same blocker SHOULD be merged when splitting does not materially reduce scanning cost. If a skill keeps only one reference file and that reference sits on the common path, its durable content SHOULD be folded back into `SKILL.md`.

## Coding-related skills

Coding-related skills MUST weight code, commands, templates, and concrete examples over explanatory prose.

- Every important rule SHOULD be anchored by runnable or readily adaptable code or commands.
- Default code-organization and authoring conventions central to the skill MUST appear in `SKILL.md` with concrete examples.
- Short common-path examples belong in `SKILL.md`; longer or conditional examples belong in purpose-complete references.
- Broken-versus-correct examples SHOULD be preferred over abstract warnings.
- Prose around templates SHOULD be compressed to the minimum needed for safe use.
- Each reference file SHOULD include at least one concrete additive example, command, config snippet, diff, or output shape tied to its blocker, and MUST NOT degrade into a prose-only checklist.

Command-heavy skills MUST present the primary decision path and first safe commands in `SKILL.md`. Command syntax in `SKILL.md` MUST be copyable and explicit. Operational cautions MUST stay adjacent to the commands they constrain. Long command catalogs, compatibility matrices, and secondary option tables SHOULD live in `references/`.

When a skill documents multiple valid workflows for the same asset class, each workflow MUST keep its own commands, paths, and output tree internally consistent. If a skill distinguishes direct-source assets from generated or rendered assets, the documentation MUST name that boundary explicitly and keep validation, render, and provisioning paths aligned to the correct side.

## Documentation style

- Markdown documents MUST prefer headings, lists, and code blocks over dense prose.
- Normative statements in stable rules documents MUST use BCP 14 language.
- Documentation examples SHOULD use the native language or tool syntax of the subject being documented.
- Comments in example code MUST use documentation comment styles: JavaDoc (Java), KDoc (Kotlin), TSDoc/JSDoc (TypeScript/JavaScript), or reStructuredText docstrings (Python). Non-documentation comments MUST NOT appear in example code.
- Blank lines MUST NOT appear inside function bodies in example code.
- Fenced code blocks MUST specify a language.
- Example code MUST use import statements over fully qualified names (FQN).
- Authors MUST verify against the official reference documentation before writing or modifying any skill content. Verification means checking command syntax, API signatures, and configuration formats against the authoritative source rather than secondary summaries.
- When example code depends on a specific version of a library, framework, language, or tool, the minimum supported version MUST be explicitly stated.
- If a review results in modifications, a follow-up review MUST be performed to verify the changes.

## Shell script conventions

All POSIX shell scripts MUST begin with this exact three-line header (in this order, no insertions):

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e
```

- Shell scripts MUST NOT use `set -u` (or any form including it: `set -eu`, `set -ue`, `set -euo pipefail`).
- Shell scripts MUST NOT use the bash/ksh extension `[[ ]]`. Tests MUST use POSIX `[ ]`. Pattern matching MUST use `case` instead of `[[ x == y* ]]`. Regex matching MUST use `grep -qE` instead of `[[ x =~ ... ]]`.
- Shell scripts MUST NOT contain standalone bracket conditional statements such as `[ -f x ] || cmd` or `[ -n "$x" ] && cmd`. Every bracket test MUST appear as the predicate of `if`, `elif`, `while`, or `until`.
- Shell scripts MUST NOT redirect any output stream to `/dev/null` (any form: `>`, `>>`, `2>`, `&>`, `1>` to `/dev/null`). Discarding output via `/dev/null` only delays the user's ability to diagnose errors. Use variable capture (e.g., `var=$(cmd 2>&1)`) or `grep -q` instead. Using `/dev/null` as an argument or input redirect (e.g., `curl -o /dev/null`, `cmd < /dev/null`) is allowed.
- Function docstrings MUST follow the JSDoc form: a description block, a blank `#` separator line, then any tag block (`@param <n>`, `@return`, `@exit`, `@throws`, `@deprecated`, `@since`, `@see`, and any other `@<word>` form). The separator MUST appear before the first tag, regardless of which tag begins the block. Comment styles other than docstrings (and the encoding marker above) MUST NOT be used in new code.
- Blank lines MUST NOT appear inside function bodies.
- Constants MUST use uppercase names; variables MUST use lowercase names.

Example canonical header with a JSDoc-style function:

```sh
#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Ensure the given path exists.
#
# @param path Filesystem path to check.
# @return Returns 1 if the path is missing.
ensure_path() {
    path="$1"
    if [ ! -e "$path" ]; then
        echo "error: '$path' not found" >&2
        return 1
    fi
}
```

## Single-file script runtime conventions

Scripts in non-shell runtimes SHOULD declare runtime dependencies inline. Each runtime has a standard mechanism for inline dependency declarations.

### Python

Python scripts SHOULD declare runtime dependencies inline using PEP 723 metadata.

```python
# /// script
# dependencies = [
#   "beautifulsoup4",
# ]
# ///

from bs4 import BeautifulSoup
```

### Deno

Deno scripts SHOULD declare dependencies using `npm:` specifiers for direct npm package consumption.

```ts
#!/usr/bin/env -S deno run

import * as cheerio from "npm:cheerio@1.0.0";
```

### Bun

Bun scripts SHOULD declare dependencies using direct npm package imports.

```ts
#!/usr/bin/env bun

import * as cheerio from "cheerio@1.0.0";
```

### Ruby

Ruby scripts SHOULD declare dependencies inline using Bundler inline gemfiles.

```ruby
require 'bundler/inline'

gemfile do
  source 'https://rubygems.org'
  gem 'nokogiri'
end
```

## AST-based modification

When modifying source files, agents and tooling SHOULD use a language-appropriate AST or PSI (Program Structure Interface) representation whenever a parser is reasonably available — for example Python's `ast`/`libcst`, TypeScript's `ts-morph`/compiler API, IntelliJ Platform's PSI for Kotlin/Java, `tree-sitter` for shell/Markdown, `roslyn` for C#, etc.

- Regex- or string-based edits MUST be a last resort. They are permitted only when (a) no robust parser is available for the language, (b) the change is purely lexical (e.g., whitespace), or (c) the file is too small for parser overhead to be worth it.
- When string-based edits are used, the agent MUST identify the exact node being modified (function name, declaration, etc.) so that a reviewer can confirm scope without re-parsing.
- Existing implementations that rely on regex or string surgery SHOULD be migrated to AST-based equivalents when feasible.

## Review checklist

Reviewers MUST verify: skill self-sufficiency, coherent-unit sizing, progressive disclosure, blocker-based (not topic-label) references, example and path consistency across workflows, and strict separation of `SKILL.md` common-case content from `references/` additive depth. All identified issues MUST be tracked and resolved, including minor ones. Reviewers MUST NOT dismiss issues as too small to fix.
