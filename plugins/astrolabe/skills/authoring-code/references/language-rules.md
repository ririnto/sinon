# Language Rules

## JavaScript And TypeScript

Place `// -*- coding: utf-8 -*-` immediately after a shebang in shebang files.
Use concrete structural JSDoc types in `.js`, `.mjs`, and `.cjs` files.
Keep package scripts free of try-catch and promise catch handlers.

## Python

Start every Python file with `# -*- coding: utf-8 -*-`.
Reconfigure executable standard streams to UTF-8 after importing `sys`.
Use standalone triple-quote block form and reStructuredText field-list docstrings.

## Kotlin

Use `buildList`, `buildSet`, and `buildMap` instead of mutable collection constructors.
When Kotlin and Java `Path` names conflict, import `kotlin.io.path.Path` and use fully qualified `java.nio.file.Path`.

## Shared Rules

Inline a single-use local only when side-effect order, type meaning, debuggability, domain meaning, lazy evaluation, exception clarity, chaining, and branch clarity remain intact.
Do not use leading-underscore file, function, variable, or class names except language-standard dunder names and standalone unused slots.
