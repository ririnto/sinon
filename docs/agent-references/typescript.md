# TypeScript Conventions

Apply these rules to TypeScript source under `scripts/` and `rules/`.

## Exports And TSDoc

Every exported declaration, exported type, re-export, and default export carries a TSDoc comment.
Write TSDoc in English in the multiline form: an opening `/**` line, meaningful English sentences on lines that begin with an asterisk, and a closing `*/` line.
Concise TSDoc states the contract, not a restatement of the name.
Add `@param` only when it clarifies a constraint, unit, nullability, or other meaning beyond the identifier and type.
Add `@return` only when it clarifies result meaning, constraints, units, or nullability beyond the signature.
An unexported helper needs no TSDoc.

## Function Bodies And Comments

Keep no blank lines inside a function body.
Blank lines between functions and lint-required spacing stay.
Put no inline comments inside a function body.
An explanation that must survive becomes a TSDoc comment on the relevant declaration.

## Bindings And Control Flow

Use `const` for values that do not need reassignment.
Prefer immutable data structures when the contract does not require mutation.
Use `readonly` for properties and arrays that must not change.
Put braces around every `if` and `else` branch, including guard returns and one-line branches.
When a guard only exits before trailing work, invert its condition and enclose that work when behavior stays identical.
Use `if`/`else` or `switch` for mutually exclusive branches when it improves clarity.
Keep validation, loop-exit, cleanup, and other genuine early exits.
Prefer recursion only when the actual call bound is stack-safe and the recursive form is clearer than an iterative form.
Do not replace an unbounded or unknown-depth loop with recursion.

## Punctuation

Use no trailing comma in TypeScript.
The repository formatter configuration enforces removal (`trailingComma: "none"`).

## Type-Safe Inlining

Inline a single-use local only when the type, evaluation count, evaluation order, exception timing, mutable snapshot, closure capture, and overload or receiver resolution stay identical.
Preserve the binding and state the concrete reason when any of them would change.

Prefer an expression body for a function that consists of a single returned expression when the return type, nullability, and API semantics stay unchanged.

Pass an existing function reference instead of a wrapper unless adaptation is needed.
