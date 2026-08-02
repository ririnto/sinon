---
name: using-workflow-tools
description: Use when structural code navigation, impact analysis, exact text search, or command-output reduction can improve an engineering task.
---

# Using Workflow Tools

## Start With an Evidence Question

State the question and the evidence required to answer it before selecting a tool.

Choose tools by evidence need rather than habit.

## Match the Tool to the Evidence

Use bounded structural navigation for symbols, relationships, architecture, callers, and flows when its index is current.

Use exact text search for configuration, documentation, generated files, and prohibited-string or absence checks.

Use raw file reads to confirm details or inspect files outside the structural index.

Use reduced command output only when exact diagnostics and complete errors remain available.

Open [references/tool-selection.md](references/tool-selection.md) when the structural exploration, text search, or output-reduction choice needs detailed guidance.

## Keep Evidence Trustworthy

Prefer one bounded structural exploration over repeated search and read loops.

Do not rebuild an index unless repository convention and task authority require it.

Use the original command when exact lines, structured output, debugging output, or complete errors matter.

Treat output adapters as optional and never as evidence themselves.

## Return the Investigation

Return the question answered, files or symbols inspected, commands used, evidence references, and uncertainty.

Separate structural findings from literal absence proofs.
