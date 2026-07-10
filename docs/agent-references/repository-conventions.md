# Repository Conventions

Open this reference when editing source, scripts, documentation, or configuration.
It supplies durable rules that do not belong in the concise project instructions.

## Shell

POSIX scripts start with `#!/usr/bin/env sh`, `# -*- coding: utf-8 -*-`, and `set -e`.
Do not use `set -u`, `[[ ]]`, standalone bracket short-circuits, output suppression to `/dev/null`, or `|| true`.
Use uppercase constants, lowercase variables, function docstrings, no blank function-body lines, and visible diagnostics.

## Source Changes

Prefer an available AST, PSI, or parser.
Use text or regex surgery only for lexical, small, or no-parser work; name the edited node when you do.
Preserve public declaration documentation and local comment conventions.

## Documentation and Configuration

Use English headings unless an existing document intentionally differs.
Leave blank lines around headings, lists, and language-tagged fences.
Use semantic line breaks, ASCII trees, and BCP 14 terms in stable rules.
YAML uses plain or double-quoted short scalars, folded blocks for wrapped logical strings, and literal blocks for meaningful line breaks.

Open the owning runtime skill for inline single-file dependency guidance.
