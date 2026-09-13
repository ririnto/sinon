# TypeScript Rules

These rules extend the common rules in `../rules.md` for TypeScript files.
Read them together with that document.
The `bun` tool reference owns TypeScript project detection, Oxc configuration, and native commands.

## Exports And TSDoc

Every exported declaration, exported type, re-export, and default export carries a TSDoc comment.
Write TSDoc in English in the multiline form: an opening `/**` line, meaningful English sentences on lines that begin with an asterisk, and a closing `*/` line.
Concise TSDoc states the contract, not a restatement of the name.
Add `@param` only when it clarifies a constraint, unit, nullability, or other meaning beyond the identifier and type.
Add `@return` only when it clarifies result meaning, constraints, units, or nullability beyond the signature.
An unexported helper needs no TSDoc.

## Function Bodies And Comments

Put braces around every `if` and `else` branch, including guard returns and one-line branches.
Use no trailing comma in TypeScript.
Keep no blank lines inside a function body.
Blank lines between functions and lint-required spacing stay.
Put no inline comments inside a function body.
An explanation that must survive becomes a TSDoc comment on the relevant declaration.

## Bindings And Recursion

Use `const` for values that do not need reassignment.
Prefer immutable data structures when the contract does not require mutation.
Use `readonly` for properties and arrays that must not change.
The common rules govern the conversion from mutable state.
Prefer recursion only when the actual call bound is stack-safe and the recursive form is clearer than an iterative form.
Do not replace an unbounded or unknown-depth loop with recursion.

## Type-Safe Inlining

Inline a single-use local only when the type, evaluation count, evaluation order, exception timing, mutable snapshot, closure capture, and overload or receiver resolution stay identical.
Preserve a binding when a wider or narrower type would result or when any of those semantics would change.
