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
- Custom rules (buildSrc): 15 rules in the `code` ruleset family:
  - `if-statement-braces`.
  - `import-over-fqn`.
  - `kotlin-top-level-declaration-count`.
  - `implicit-lambda-it`.
  - `leading-underscore`.
  - `unchecked-cast-suppression`.
  - `non-null-assertion`.
  - `multiline-doc-style`.
  - `unstructured-logging`.
  - `companion-object-position`.
  - `explicit-property-type`.
  - `comparison-direction`.
  - `terminal-branch-when`.
  - `public-declaration-doc-comment`.
  - `slf-direct-logging`.
- EditorConfig knobs:
  - `ktlint_multiline_doc_style_mode`.
  - `ktlint_companion_object_position` (`top`, `bottom`, or `any`).
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
