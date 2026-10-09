---
metadata:
  reference:
    Node.js releases:
      url: https://nodejs.org/en/about/previous-releases
    Node LTS TypeScript base:
      version: 24.0.1
      url: https://registry.npmjs.org/@tsconfig%2fnode-lts/24.0.1
    TypeScript compiler options:
      url:
        - https://www.typescriptlang.org/tsconfig/target.html
        - https://www.typescriptlang.org/tsconfig/lib.html
    pnpm catalogs:
      url: https://pnpm.io/catalogs
    pnpm installation:
      version: 12.10.1
      url: https://pnpm.io/installation
---

# TypeScript Conventions

Apply these rules to TypeScript source under `scripts/` and `rules/`.
Use [Source Changes](repository-conventions.md#source-changes) for shared source rules, including function-body spacing.

## Runtime And Tooling

Use the current Node.js development runtime in the `package.json` engine range.
All repository scripts, custom lint rules, and tests run with Node.js.
Use the configured TypeScript loader for source files that Node.js cannot load directly.
Run Ultracite, Markdownlint, TypeScript, and Vitest through the repository's pnpm scripts.

## Exports And TSDoc

Every exported declaration, exported type, re-export, and default export carries a TSDoc comment.
Write TSDoc in English in the multiline form: an opening `/**` line, meaningful English sentences on lines that begin with an asterisk, and a closing `*/` line.
Concise TSDoc states the contract, not a restatement of the name.
Add `@param` only when it clarifies a constraint, unit, nullability, or other meaning beyond the identifier and type.
Add `@return` only when it clarifies result meaning, constraints, units, or nullability beyond the signature.
An unexported helper needs no TSDoc.

## Function Bodies And Comments

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

Preserve the binding's TypeScript type.

Prefer an expression body for a function that consists of a single returned expression when the return type, nullability, and API semantics stay unchanged.

Pass an existing function reference instead of a wrapper unless adaptation is needed.
