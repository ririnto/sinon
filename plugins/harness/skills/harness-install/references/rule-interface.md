# Native Enforcement Model

Document the native-linter enforcement model.
Each stack uses its ecosystem's native tool, including:

- ktlint.
- Checkstyle.
- Spotless.
- Ruff.
- Ultracite.
- ShellCheck.
- shfmt.

Stacks may add native custom rules and structural prose conventions.

## Enforcement Inventory

Each stack delegates code-style and structure detection to its native ecosystem tools.
Structural checks that cannot be automated remain prose conventions in the installed-target contract.

### Gradle

- Validator: `./gradlew ktlintCheck` (custom ruleset family `code`, rule ids `code:<kebab>`, using the Gradle ktlint plugin's project discovery)
- Custom rules (buildSrc): 21 rules in the `code` ruleset family:
  - `control-flow-braces` (autocorrect wraps non-empty unbraced bodies of `if`, `else` excluding else-if chains, `for`, `while`, and `do-while`).
  - `import-over-fqn` (autocorrect rewrites fully-qualified references to short names and inserts the deduplicated import, when eight safety preconditions hold: no same-name unaliased import of a different path, no same-path aliased import, no conflicting star import, no top-level declaration collision, no conflicting import alias, no same-name overload at any usage site, no same-name local/parameter/receiver shadow, and the FQN package differs from the file's own package).
  - `kotlin-top-level-declaration-count` - Non-script files require exactly one class, object, interface, enum, annotation, or type alias.
    - This rule is lint-only because filesystem splitting cannot be automated.
  - `implicit-lambda-it` - Autocorrect inserts an explicit `it ->` parameter.
    - Multi-line lambdas place the arrow on its own line at body indentation.
  - `leading-underscore` - Autocorrect renames a private non-override unused parameter to `_` when no sibling collision, no call-site named argument, and no body reference exist.
    - File basenames, override/open/abstract function parameters, and override/interface properties may have leading underscores.
  - `unchecked-cast-suppression` (autocorrect removes a stale `@Suppress("UNCHECKED_CAST")` annotation when its scope contains no `as` or `as?` operator).
  - `non-null-assertion` - Flags every `!!` usage.
    - Autocorrect strips `!!` from `requireNotNull(...)` or `checkNotNull(...)`.
    - For any other operand, autocorrect wraps it in `requireNotNull(...)` while preserving trailing member access chains.
    - `requireNotNull` is in the Kotlin standard library and needs no import.
    - This rule is lint-only when any user-defined function named `requireNotNull` or `checkNotNull` is declared in the same file.
    - This prevents the rewritten call from silently resolving to the user declaration.
  - `multiline-doc-style` - Disabled by default.
    - Opt in via `ktlint_multiline_doc_style_mode = multiline` or `on`.
    - Autocorrect expands one-line KDoc to multi-line.
  - `unstructured-logging` (lint-only because the requested name or behavior change cannot be invented deterministically).
  - `companion-object-position` - Flags companion objects that are not the first non-enum-entry declaration in their class or object body.
    - This rule is lint-only because reordering declarations changes initialization order and may break forward references.
  - `explicit-property-type` - Autocorrect inserts the type for class-body properties whose initializer is a bare literal whose Kotlin type is unambiguous: `String`, `Boolean`, `Char`, `Int`, `Long` via `L` suffix, `Double`, `Float` via `f` suffix.
    - Null literal, unsigned-suffixed literals, and prefix-unary expressions remain lint-only.
  - `comparison-direction` (lint-only because swapping operands is not generally equivalent for custom `Comparable` implementations).
  - `terminal-branch-when` - This rule is lint-only because subject detection requires resolver-level type analysis.
    - A no-subject `when` would not satisfy the rule's stated intent.
  - `public-declaration-doc-comment` - Disabled by default.
    - Opt in via `ktlint_public_declaration_doc_comment_mode = on` or `public`.
    - This rule is lint-only and flags public APIs missing KDoc.
  - `slf-direct-logging` (lint-only because receiver type cannot be proven from the AST).
  - `no-regex-constructor` - Autocorrects the single-positional string-template form `Regex("...")` to `"...".toRegex()`.
    - Hoisting into top-level or companion `val` remains lint-only because identity, placement, and visibility cannot be derived deterministically.
  - `no-decorative-function-body-blank-lines` - Autocorrect collapses multi-blank-line whitespace inside direct named function body blocks to a single newline plus the body indentation.
    - Nested lambda bodies are not covered.
    - Blank lines between declarations, between local functions, and adjacent to comments are preserved.
  - `explicit-function-return-type` - Autocorrect inserts the type for expression-bodied functions whose body is a bare literal whose Kotlin type is unambiguous.
    - `override`, `external`, `expect`, and `suspend` functions remain lint-only, as do bodies that are calls, binary expressions, `if`/`when`, or prefix-unary expressions.
  - `nested-data-class-last` - Autocorrect moves offending nested `data class` declarations to the end of their enclosing class body via stable partition.
    - Forward references to nested types are safe in Kotlin, so the move preserves semantics.
  - `no-import-alias` - This rule is lint-only because rewriting an aliased import requires choosing a fresh identifier and verifying no collision at every use site.
    - It flags only redundant aliases where the alias matches the imported simple name, such as `import a.Foo as Foo`.
  - `no-line-comment` - This rule is lint-only.
    - It forbids all `//` and `/* ... */` comments and directs users to use KDoc instead.
    For example, `// note` and `/* note */` are flagged, while `/** note */` is allowed).
  - Guidance-only patterns (not automated because the rewrite is ambiguous or requires non-local dataflow).
    - These patterns are covered by the review checklist instead of a rule:
  - `nullable-elvis-return`: prefer returning a nullable lookup as a single expression with `let` and an explicit parameter over `val local = nullable ?: return fallback`.
- EditorConfig knobs:
  - `ktlint_multiline_doc_style_mode` - Defaults to `off`.
    - Set to `multiline` or `on` to enable.
  - `ktlint_public_declaration_doc_comment_mode` - Defaults to `off`.
    - Set to `on` or `public` to enable.
  - `ktlint_standard_trailing-comma-on-call-site`.
  - `ktlint_standard_trailing-comma-on-declaration-site`.
- Standard ktlint ruleset: also runs alongside custom rules.
- Script handling: Expression, statement, naming, and member-style custom rules run on `.kt` and `.kts`.
  file/API structure rules (`kotlin-top-level-declaration-count`, `public-declaration-doc-comment`) skip `.kts` script files.
- Check command: `./gradlew ktlintCheck`
- Fix command: `./gradlew ktlintFormat` runs `markdownlint-cli2 --fix` when it is available on PATH.
  It then uses the Gradle ktlint plugin's project discovery.

### Maven

- Validator: The installer-generated Maven command uses `git ls-files` to build escaped, repo-root-anchored `spotlessFiles`.
  It runs `./mvnw validate` with `-DspotlessFiles` when files are present.
  Spotless enforces Java formatting with the same repo-root-anchored file set.
  Checkstyle lints imports and braces, while Spotless applies the Palantir Java formatter, import order, unused import removal, and trailing-whitespace hygiene.
- Remaining Java code-structure detection rules are prose-only.
- Check command: Generated Maven Checkstyle plus Spotless command with `git ls-files` and `spotlessFiles`.
- Fix command: Use the generated Maven `git ls-files` wrapper shape with `./mvnw exec:exec@format-markdown spotless:apply` and the same escaped, repo-root-anchored `spotlessFiles` value.
  `exec:exec@format-markdown` applies Markdown fixes.
  `spotless:apply` applies Java formatting with the same coverage as the check command.
  Checkstyle only lints and has no fix step.

### uv

- Validator: `uv run scripts/check.py`.
  It runs `uv run --with "ruff>=0.15.21,<0.16.0" ruff check .`.
  It runs `uv run --with "ruff>=0.15.21,<0.16.0" ruff format --check .`.
  It uses Ruff's project discovery.
- Ruff configuration: `ruff.toml` at repository root.
  keeps Ruff lint defaults and sets ruff format quote style.
- Custom Python AST conventions: Seven prose-only rules.
  - Rules:
    - `leading-underscore`.
    - `multiline-doc-style`.
    - `unstructured-logging`.
    - `public-declaration-doc-comment`.
    - `unchecked-cast-suppression`.
    - `triple-quote-inline-comment`.
    - `mutable-collection`.
These are documented code conventions, not automated enforcement.
- Fix command: `uv run scripts/fix.py`.
  It runs `markdownlint-cli2 --fix` when it is available on PATH.
  It runs `uv run --with "ruff>=0.15.21,<0.16.0" ruff check --fix .`.
  It runs `uv run --with "ruff>=0.15.21,<0.16.0" ruff format .`.
  It uses Ruff's project discovery.
- Quote style: ruff format uses double quotes.

### Bun

- Validator: `bun run check` (package.json script running packaged `markdownlint-cli2` and `ultracite check`, with the Ultracite preset over oxlint).
- Fix command: `bun run fix` (package.json script running packaged `markdownlint-cli2 --fix` and `ultracite fix`).
- Configuration files: `oxlint.config.ts` and `oxfmt.config.ts` (extend `ultracite/oxlint/core` with `core.ignorePatterns` and `ultracite/oxfmt`).
- JSDoc enforcement (oxlint): `oxlint.config.ts` follows the Ultracite provider shape.
  It relies on `extends: [core]` for the preset plugin set, including the built-in `jsdoc` plugin.
  - It adds a JavaScript-only override for `**/*.{js,jsx,mjs,cjs}` with `jsdoc/require-param`, `jsdoc/require-param-type`, `jsdoc/require-param-name`, `jsdoc/require-returns`, and `jsdoc/require-returns-type` set to `deny`.
  - TypeScript does not need an override for those tag rules because Ultracite core already sets them to `off`.
- TypeScript public API docs (local oxlint JS plugin): `scripts/tsdoc-plugin.ts` provides `tsdoc/require-export-tsdoc`.
  The rule requires TSDoc on directly exported TypeScript top-level functions, variables, constants, and classes.
  It also requires TSDoc on public methods and accessors on directly exported classes.
  - The plugin name `tsdoc` is local and does not collide with oxlint's documented built-in plugin names.
- Tool provisioning: run `bun install` before `bun run check`.
  package scripts use installed dependencies and fail if those dependencies are absent or broken.

### Shell

- Validator: `sh scripts/check.sh` (wrapper invoking native `shellcheck` and `shfmt -d`)
- Fix command: `sh scripts/fix.sh` (wrapper invoking `markdownlint-cli2 --fix` when it is available on PATH, then `shfmt`, then rerunning `shellcheck` and `shfmt -d` through `scripts/check.sh`)

Every mode ships both hook stages under `.githooks/`.
The installer copies those files without activating them.
Only `--activate-hooks` MAY set repository-local `core.hooksPath` to `.githooks/`.
Ordinary dependency installation, synchronization, and build commands MUST NOT activate hooks.

## Installed Command Contract

The installed `pre-commit` hook and selected CI configuration run the stack's canonical check command.
The installed `pre-push` hook may add the stack's test command.
Both hook files remain inactive until `--activate-hooks` sets repository-local `core.hooksPath` to `.githooks/`.
The installer validates hook executability and command agreement before activation.
