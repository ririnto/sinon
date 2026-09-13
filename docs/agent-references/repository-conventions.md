# Repository Conventions

Open this reference when editing source, scripts, documentation, or configuration.

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
Authoritative public standards and vendor documentation citations may stay.
Use real `#` heading syntax for structural headings.
Do not use bold text on its own line as a pseudo-heading; a natural prose lead-in to a code block does not need to become a heading.

## Official References

Reference content derived from an official source requires reading the authoritative upstream documentation directly, and recording the exact source used: page, version, and read date.
Never fabricate a commit, version, or read date.
Keep the original content faithful within the license and copyright of the source, and preserve its licensing and attribution.
Distinguish exact preservation of official source from our own authored or adapted examples: label an adapted example as adapted, and never call edited code verbatim.

Open the owning runtime skill for inline single-file dependency guidance.
