# JavaScript

Use the JavaScript language rules for `.js`, `.jsx`, `.mjs`, and `.cjs` files.

## Documentation

Document exported functions, classes, and other public declarations with JSDoc when the declaration has a public contract.
Use `@param` and `@returns` tags when they clarify the accepted inputs or returned value.
Do not add a type tag when the JavaScript syntax already expresses the type through a stable public contract.
Document public class methods unless the class member is private or a constructor.

## Bindings And Control Flow

Prefer immutable `const` bindings.
Use `let` only when the binding must be reassigned.
Use braces for multi-statement control-flow branches.
Keep function bodies free of blank lines and inline comments.
Use a declaration-level JSDoc block when an explanation must remain with the code.

## JSX

Keep JSX expressions small and move non-trivial decisions into named functions or values.
Give meaningful images alternative text.
Keep interactive elements keyboard reachable and use semantic elements before adding ARIA roles.

## Recursion

Do not use unbounded recursion for data or file sizes controlled by a caller.
Use an iterative traversal or an explicit work list when input depth can exceed the JavaScript stack.

## Source References

The JavaScript language guidance is adapted from the shared Harness rules and the historical Bun profile.
It is paired with the native Oxlint and Oxfmt configuration installed by the Bun profile.
