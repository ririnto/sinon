---
metadata:
  reference:
    Rethinking Skills and Prompts for GPT-6 Astra:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
---

# Repository Conventions

Use the sections for the files under change.
Shell and TypeScript sections apply to source, documentation sections to prose, and YAML rules to configuration.

## Shell

POSIX scripts start with `#!/usr/bin/env sh`, `# -*- coding: utf-8 -*-`, and `set -e`.
Do not use `set -u`, `[[ ]]`, standalone bracket short-circuits, output suppression to `/dev/null`, or `|| true`.
Use uppercase constants, lowercase variables, function docstrings, no blank function-body lines, and visible diagnostics.

## TypeScript

For TypeScript source, follow [TypeScript Conventions](typescript.md): exported-declaration TSDoc in English multiline form, no blank function-body lines, declaration-level comments, immutable bindings, and type-preserving inlining.

## Source Changes

Prefer an available AST, PSI, or parser.
Use text or regex surgery only for lexical, small, or no-parser work, and name the edited node when you do.
Preserve public declaration documentation and local comment conventions.
Keep function bodies free of blank lines, and separate function or class declarations with one blank line.

Write all source documentation comments in English.
Use the language's multiline documentation form.
State a contract or reason instead of restating the identifier.

## Documentation and Configuration

Use English headings unless an existing document intentionally differs.
Leave blank lines around headings, lists, and language-tagged fences.
Use semantic line breaks, ASCII trees, and BCP 14 terms in stable rules.
YAML uses the `.yaml` extension unless a host or tool contract requires `.yml` (for example `.gitlab-ci.yml` at a repository root).
YAML uses plain or double-quoted short scalars, folded blocks for wrapped logical strings, and literal blocks for meaningful line breaks.
YAML sequences use block style; keep a flow sequence only inside an explicit empty sequence (`key: []`), because a block form cannot express an empty sequence without changing it to null.
Do not quote a scalar when its parsed type and value already match the consumer's need.
Use double quotes only when the actual consumer requires the exact string type or value, such as a YAML 1.1 scalar that would coerce, or a version-like or numeric-key scalar that must stay a string.

## Markdown Authoring

Write one prose sentence per source line, including nested-list continuation lines.
Do not pack multiple sentences into one line with semicolons.
Indent nested-list continuation lines to align under their item text.
Keep fenced code blocks, YAML frontmatter, tables, and link syntax intact, and never split a sentence inside code, URLs, or version strings.
Relative references that load instruction or implementation content must stay inside the same publishable plugin.
Record authoritative public standards and vendor documentation citations in frontmatter, not in body prose.
Use real `#` heading syntax for structural headings.
Do not use bold text on its own line as a pseudo-heading; a natural prose lead-in to a code block does not need to become a heading.

## Instruction And Skill Authoring

Keep activation descriptions short and specific to the task that needs the skill.
Use skill roots as small routers; name when each supporting reference applies.
Keep each rule with its existing owner instead of repeating it across consumers.
Specify the required outcome, authority, and completion boundary rather than a fixed itinerary for every task.
Match proof to acceptance criteria and changed behavior; do not add tests that only restate prose.
Preserve explicit user requirements, safety boundaries, and required source standards when simplifying guidance.

## Dependency Selection

Before adding or upgrading a library or tool, check its registry for the latest stable version compatible with the target's runtime and dependency constraints.
Use the relevant registry, such as npm, Maven Central, PyPI, or crates.io, rather than treating a version in guidance as current.
Respect the target's lockfile, parent POM, BOM, version catalog, and existing pins.
Treat historical version examples as examples.
Change a target pin only when the task authorizes that dependency change.

## Official References

Reference content derived from an official source requires reading the authoritative upstream documentation directly.
Record sources for a skill and its child references once in the owning `SKILL.md` frontmatter under `metadata.reference`.
Use concise source names as keys.
For documents without an owning skill, keep source metadata in that document's frontmatter.
Omit redundant words such as `Reference` or `README` from source names.
Record its version, tag, or other appropriate identifier in a separate field when available.
Do not repeat the source name in a version value unless it belongs to the official version label.
Use a list of mappings under one source name when distinct versions have distinct URLs.
Keep each `version` or `tag` and its corresponding `url` in the same mapping.
Do not add a read date.
Use `url` with a scalar for one source page or a list for multiple pages of the same source version.
Never fabricate a commit or version.
Keep the original content faithful within the license and copyright of the source, and preserve its licensing and attribution.
Distinguish exact preservation of official source from our own authored or adapted examples: label an adapted example as adapted, and never call edited code verbatim.

Open the owning runtime skill for inline single-file dependency guidance.
