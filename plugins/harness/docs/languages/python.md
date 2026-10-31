---
title: Python Language Rules
metadata:
  reference:
    Python:
      url:
        - https://docs.python.org/3/library/pathlib.html
        - https://docs.python.org/3/library/typing.html#typing.Final
---

This document owns Python-specific rules for the `uv` profile.
The [shared rules](../rules.md) apply alongside these Python-only invariants.
The [uv tool reference](../tools/uv.md) owns commands and configuration.

## Bindings And Data

Treat function parameters, loop targets, and module-level constants as immutable by convention.
Use `Final` for module-level constants that name fixed values.
Use `frozenset` and tuple literals for fixed collections exposed across modules.
Never reach for `deepcopy` or defensive copies unless a caller can actually mutate the shared value.

Preserve evaluation order and exception timing when converting mutable state to immutable forms.
A generator expression evaluates lazily.
A list comprehension evaluates eagerly.
Choose deliberately when evaluation count or error timing is observable.

## Strings And Errors

Use f-strings for interpolation.
They evaluate eagerly and keep format logic local.
Handle specific exception types at the narrowest scope that can recover.
Catch `Exception` only at a documented boundary, and translate every caught failure into a surfaced message, a typed result, or a logged gap.
Never swallow an exception silently.

Use `pathlib.Path` over string path surgery.
Parse structured data with the standard parsers (`json`, `urllib.parse`, `datetime.fromisoformat`) instead of string matching.

## Syntax And Style

Match the Ruff configuration's `quote-style` and formatting decisions.
Keep type hints on public functions.
Private helper return annotations remain optional outside special, static, and class methods.
Ruff also requires argument annotations on private helpers because its argument checks do not distinguish public visibility.
Prefer `match` statements over long `if/elif` chains when dispatching on one value.
JS-style braces and Kotlin syntax do not apply to Python.
Use Python's suite syntax and docstrings.
Recursion is bounded by the interpreter stack.
Convert deep recursion to iteration when depth scales with input size.

## Documentation

Document meaningful public module, class, and function contracts in imperative prose.
Include parameter semantics, return behavior, and raised errors when those details help callers.
Do not add boilerplate to trivial declarations merely to satisfy a missing-docstring rule.
Use multiline docstrings, including one-sentence docstrings.
Put opening and closing triple quotes on separate lines.
One short paragraph states the contract.
Add parameters and return descriptions only when they clarify semantics.
Do not restate the type hints in prose.
Ruff checks quote placement for multiline docstrings but cannot require single-line docstrings to expand.
Review that remaining requirement manually.
