# Python Language Rules

This document owns Python-specific rules for the `uv` profile.
The [shared rules](../rules.md) apply alongside these Python-only invariants.
The [uv tool reference](../tools/uv.md) owns commands and configuration.

## Bindings And Data

Treat function parameters, loop targets, and module-level constants as immutable by convention.
Use `Final` for module-level constants that name fixed values.
Use `frozenset` and tuple literals for fixed collections exposed across modules.
Never reach for `deepcopy` or defensive copies unless a caller can actually mutate the shared value.

Preserve evaluation order and exception timing when converting mutable state to immutable forms.
A generator expression evaluates lazily; a list comprehension evaluates eagerly.
Choose deliberately when evaluation count or error timing is observable.

## Strings And Errors

Use f-strings for interpolation; they evaluate eagerly and keep format logic local.
Handle specific exception types at the narrowest scope that can recover.
Catch `Exception` only at a documented boundary, and translate every caught failure into a surfaced message, a typed result, or a logged gap.
Never swallow an exception silently.

Use `pathlib.Path` over string path surgery.
Parse structured data with the standard parsers (`json`, `urllib.parse`, `datetime.fromisoformat`) instead of string matching.

## Syntax And Style

Match the Ruff configuration's `quote-style` and formatting decisions.
Keep type hints on public functions; do not chase full annotation coverage of private glue code.
Prefer `match` statements over long `if/elif` chains when dispatching on one value.
JS-style braces and Kotlin syntax do not apply to Python; use Python's suite syntax and docstrings.
Recursion is bounded by the interpreter stack; convert deep recursion to iteration when depth scales with input size.

## Documentation

Write module, class, and public-function docstrings in imperative prose.
One short paragraph states the contract; add parameters and return descriptions only when they clarify semantics.
Do not restate the type hints in prose.
