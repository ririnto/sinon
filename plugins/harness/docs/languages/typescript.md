---
title: TypeScript Rules
metadata:
  reference:
    TypeScript:
      url:
        - https://www.typescriptlang.org/docs/handbook/2/everyday-types.html
        - https://tsdoc.org/pages/intro/approach/
---

These rules extend the [common rules](../rules.md) for TypeScript files.
Read them together with that document.
The [pnpm tool reference](../tools/pnpm.md) owns TypeScript project detection, Oxc configuration, and native commands.

## Exports And TSDoc

Document meaningful public contracts with TSDoc at the declaration.
The configured check requires comments on public classes, interfaces, enums, namespaces, functions, callable or class-valued variables, and selected public class members.
Comments on type aliases and non-callable variables are optional, but must use the same multiline format when present.
For a locally declared identifier exported through a list, default export, or export assignment, the configured check accepts documentation on either the declaration or the export.
External re-exports inherit documentation from their originating declarations.
The check does not inspect non-identifier default or assignment expressions, so review their public contracts manually.
Document each public class method, abstract method, accessor, and function-valued property as well.
Write TSDoc in English in the multiline form: an opening `/**` line, meaningful English sentences on lines that begin with an asterisk, and a closing `*/` line.
Concise TSDoc states the contract, not a restatement of the name.
Add `@param` only when it clarifies a constraint, unit, nullability, or other meaning beyond the identifier and type.
Add `@return` only when it clarifies result meaning, constraints, units, or nullability beyond the signature.
Explain error behavior and link related public declarations when those details help callers.
The lint check enforces comment presence and delimiter placement, not whether the prose conveys a useful contract.
An unexported helper needs no TSDoc.

## Function Bodies And Comments

Tool directive comments such as `@ts-expect-error` and `oxlint-disable-next-line` are the only inline comments allowed in a function body.
Put braces around every `if` and `else` branch, including guard returns and one-line branches.
Use no trailing comma in TypeScript.

## Bindings And Recursion

Use `const` for values that do not need reassignment.
Prefer immutable data structures when the contract does not require mutation.
Use `readonly` for properties and arrays that must not change.
The common rules govern the conversion from mutable state.
Prefer recursion only when the actual call bound is stack-safe and the recursive form is clearer than an iterative form.
Do not replace an unbounded or unknown-depth loop with recursion.

## Type-Safe Inlining

Apply the common inlining rules and preserve the binding's TypeScript type.
