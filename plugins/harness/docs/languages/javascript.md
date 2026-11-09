---
metadata:
  reference:
    JavaScript:
      url:
        - https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Statements/const
        - https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA
---

# JavaScript

Use the JavaScript language rules for `.js`, `.jsx`, `.mjs`, and `.cjs` files.
The [shared rules](../rules.md) apply alongside these JavaScript-only invariants.
The [pnpm tool reference](../tools/pnpm.md) owns commands and configuration.

## Documentation

Document exported functions, classes, and other public declarations with JSDoc when the declaration has a public contract.
Use `@param` and `@returns` tags when they clarify the accepted inputs or returned value.
Do not add a type tag when the JavaScript syntax already expresses the type through a stable public contract.
Document public class methods unless the class member is private or a constructor.
Use multiline JSDoc in maintained source and code samples.
Put the opening `/**` and closing `*/` delimiters on separate lines.

## Bindings And Control Flow

Prefer immutable `const` bindings.
Use `let` only when the binding must be reassigned.
Use braces for multi-statement control-flow branches.

## JSX

Keep JSX expressions small and move non-trivial decisions into named functions or values.
Use semantic elements before adding ARIA roles.
Apply the shared accessibility rules to JSX interfaces.

## Recursion

Do not use unbounded recursion for data or file sizes controlled by a caller.
Use an iterative traversal or an explicit work list when input depth can exceed the JavaScript stack.
