# Repository Conventions

Open this reference when editing source, scripts, documentation, or configuration.

## Shell

POSIX scripts start with `#!/usr/bin/env sh`, `# -*- coding: utf-8 -*-`, and `set -e`.
Do not use `set -u`, `[[ ]]`, standalone bracket short-circuits, output suppression to `/dev/null`, or `|| true`.
Use uppercase constants, lowercase variables, function docstrings, no blank function-body lines, and visible diagnostics.

## Source Changes

Prefer an available AST, PSI, or parser.
Use text or regex surgery only for lexical, small, or no-parser work, and name the edited node when you do.
Preserve public declaration documentation and local comment conventions.

## Documentation and Configuration

Use English headings unless an existing document intentionally differs.
Leave blank lines around headings, lists, and language-tagged fences.
Use semantic line breaks, ASCII trees, and BCP 14 terms in stable rules.
YAML uses plain or double-quoted short scalars, folded blocks for wrapped logical strings, and literal blocks for meaningful line breaks.

## Markdown Authoring

Write one prose sentence per source line, including nested-list continuation lines.
Do not pack multiple sentences into one line with semicolons.
Indent nested-list continuation lines to align under their item text.
Keep fenced code blocks, YAML frontmatter, tables, and link syntax intact, and never split a sentence inside code, URLs, or version strings.
Relative references that load instruction or implementation content must stay inside the same publishable plugin.
Authoritative public standards and vendor documentation citations may stay.

Open the owning runtime skill for inline single-file dependency guidance.
