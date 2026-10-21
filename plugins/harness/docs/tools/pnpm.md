---
title: pnpm
metadata:
  reference:
    pnpm:
      version: 12.10.1
      url:
        - https://pnpm.io/installation
        - https://pnpm.io/catalogs
        - https://pnpm.io/cli/install
    pnpm setup:
      version: 3.0.0
      url: https://raw.githubusercontent.com/pnpm/setup/v3.0.0/README.md
    Node.js:
      url: https://nodejs.org/en/about/previous-releases
    Corepack:
      url: https://nodejs.org/docs/latest-v24.x/api/corepack.html
    Oxlint:
      version: 1.87.0
      url:
        - https://oxc.rs/docs/guide/usage/linter/config.md
        - https://oxc.rs/docs/guide/usage/linter/writing-js-plugins.md
        - https://www.npmjs.com/package/oxlint/v/1.87.0
    Oxfmt:
      version: 0.72.0
      url:
        - https://oxc.rs/docs/guide/usage/formatter/config.md
        - https://www.npmjs.com/package/oxfmt/v/0.72.0
    Markdownlint CLI2:
      version: 0.23.3
      url: https://raw.githubusercontent.com/DavidAnson/markdownlint-cli2/v0.23.3/README.md
    Markdownlint:
      version: 0.41.1
      url:
        - https://raw.githubusercontent.com/DavidAnson/markdownlint/v0.41.1/doc/Rules.md
        - https://raw.githubusercontent.com/DavidAnson/markdownlint/v0.41.1/doc/CustomRules.md
    Maximum one sentence rule:
      version: 0.0.2
      url:
        - https://registry.npmjs.org/markdownlint-rule-max-one-sentence-per-line/0.0.2
        - https://raw.githubusercontent.com/aepfli/markdownlint-rule-max-one-sentence-per-line/main/README.md
    Ultracite:
      version: 8.0.1
      url: https://raw.githubusercontent.com/haydenbleasel/ultracite/ultracite%408.0.1/packages/cli/README.md
    TypeScript compiler options:
      url:
        - https://www.typescriptlang.org/tsconfig/exactOptionalPropertyTypes.html
        - https://www.typescriptlang.org/tsconfig/noUncheckedIndexedAccess.html
        - https://www.typescriptlang.org/tsconfig/verbatimModuleSyntax.html
    npm-run-all2:
      version: 9.0.3
      url: https://registry.npmjs.org/npm-run-all2/9.0.3
    Husky:
      version: 9.1.7
      url:
        - https://raw.githubusercontent.com/typicode/husky/v9.1.7/docs/get-started.md
        - https://raw.githubusercontent.com/typicode/husky/v9.1.7/docs/how-to.md
    Oxc Project Types:
      version: 0.153.0
      url: https://registry.npmjs.org/@oxc-project/types/0.153.0
---

Use this profile for a pnpm-managed JavaScript, TypeScript, or JSX project.
Use the [project baseline](baseline.md) for eligibility and fresh-root materialization.
For frontend or React code, also read [frontend guidance](frontend.md).

## Detection And Selection

Select pnpm explicitly when the target uses pnpm.
Automatic detection requires `pnpm-lock.yaml`, `pnpm-workspace.yaml`, or a `packageManager` value beginning with `pnpm@`.
A `package.json` file alone does not prove that the target uses pnpm.
Inspect each package root in a monorepo and install the profile at each selected root.

## Managed Sources

The profile provides `oxlint.config.ts`, `oxfmt.config.ts`, `plugins/`, `rules/`, and `.markdownlint-cli2.jsonc` as one native lint and format source.
Copy `plugins/` next to `oxlint.config.ts` because its `jsPlugins` entries use `./plugins/*.ts` paths.
Copy `rules/` next to `.markdownlint-cli2.jsonc` because its `customRules` entries use `rules/*.ts` paths.
Omit the `*.test.ts` files from both copies.
It provides `.editorconfig` only when the target has no existing editor configuration or when the user approves a bounded merge.
The fragment is a regular source file, not an instruction to overwrite an existing editor configuration.
It does not provide a complete replacement `package.json`.
Copy `package.fragment.json`, `pnpm-workspace.fragment.yaml`, and `tsconfig.fragment.json` as merge guidance only.
Do not copy any fragment over an existing manifest, workspace configuration, or compiler configuration.

## Bounded Manifest Merge

Preserve the target package name, workspaces, package manager, dependency versions, scripts, and unrelated fields.
Add missing `check`, `fix`, `pre-commit`, `pre-push`, and `prepare` script entries after reviewing existing commands.
Add `test` only when the target has a native test owner.
If an existing script owns one of these names, preserve it and report the required command substitution.
Do not silently replace an existing native check, formatter, test runner, or build task.
Before adding or upgrading any profile dependency, check the npm registry for its latest stable release compatible with the target's runtime, Oxc plugin API, and lockfile.
Keep existing compatible target pins and lockfile choices.
Store shared versions in `pnpm-workspace.yaml` catalogs and reference them with `catalog:` or the target's named catalog.
Merge missing catalog entries without replacing existing versions or unrelated catalogs.
Preserve existing workspace package globs and unrelated settings when merging `pnpm-workspace.yaml`.
Resolve catalog references from the target's workspace root before running installation.
Merge the `allow-builds.fragment.yaml` entry for esbuild only when the selected Node tooling requires its native binary installation.
Preserve existing build approval policy and review conflicts instead of approving every dependency's scripts.
Do not add direct `oxlint` or `oxfmt` script entries.
Route lint and format execution only through Ultracite.
Add `npm-run-all2` only when the merged scripts use `run-p`.
Add `@oxc-project/types` only when the target keeps TypeScript custom plugins and the target's Oxc version requires its declarations.
Use the [Git hook integration](#git-hook-integration) for the script surface.

## Git Hook Integration

Add the compatible Husky catalog entry and development dependency when installing this script surface.
The `prepare` script runs `husky` to install its Git dispatchers.
Copy `.husky/pre-commit`, `.husky/pre-push`, and `.husky/.gitignore` from the pnpm tooling source to the selected Git root.
The hooks invoke `pnpm run pre-commit` and `pnpm run pre-push` respectively.
Both pnpm hook scripts run the target's `check` gate.
The Gradle profile adds its selected native Gradle task after that gate.
Keep the generated `.husky/_/` dispatchers ignored.

Inspect `core.hooksPath`, existing Git hooks, and any existing `prepare` command before installation.
Merge an existing lifecycle command explicitly and preserve its behavior.
Resolve an existing hook manager's ownership before installing Husky instead of creating competing dispatchers or overwriting hooks.
In a monorepo, keep one hook owner at the Git root and compose the selected package checks through that owner's scripts.
Do not install independent Git dispatchers in each package.
After merging, run `pnpm install` and `pnpm run prepare` at the selected root.
Verify actual Git commit and push invocations in a disposable repository, including a failing check that blocks each operation.
Manual script execution alone does not prove hook installation.

The source configuration extends the target-installed Ultracite Oxc presets.
The style plugin checks physical blank lines inside executable function bodies and allows documentation only at recognized declarations.
It diagnoses other comments without deleting them and preserves license markers.
Sibling tests and lifecycle hooks receive one blank line only when an unshadowed named import from Vitest, Jest globals, or Bun proves the registration boundary.
Direct `describe` callbacks and program-level registrations are recognized, including named import aliases, static `only`, `skip`, `concurrent`, and `sequential` modifiers, and direct `each` factories.
Global names, namespace calls, unknown factories, shadowed bindings, ordinary callbacks, and gaps containing comments remain outside the registration-spacing diagnostic.
Required directives between registrations stay intact because moving them can change their target.
The built-in test-padding rule supplies padding around test calls but does not express this exact sibling lifecycle and proven-binding contract, so the AST-aware diagnostic handles it without an automatic fix.
It exempts recognized tool directive comments, such as `@ts-expect-error`, `@ts-ignore`, `eslint-disable*`, `oxlint-disable*`, `oxfmt-ignore`, `prettier-ignore`, and `v8 ignore`.
The TSDoc plugin enforces the [TypeScript declaration documentation requirements](../languages/typescript.md#exports-and-tsdoc).
Read those requirements when configuring TypeScript documentation checks.
Protected, private, and `#private` members and constructors are exempt.
It reports missing documentation separately from invalid delimiter placement.
The JavaScript override keeps the historical JSDoc parameter and return checks while allowing return descriptions.
The custom plugin API is alpha and must be checked against the target Oxlint version before updates.
Markdownlint support is a common shared capability installed at the target project root.
Merge the canonical `.markdownlint-cli2.jsonc` from this profile into an existing target configuration, or create it when none exists.
The profile ships its custom Markdownlint rules under `rules/`.
`docs/no-box-drawing` rejects Unicode box drawing characters.
`docs/table-separators` requires exactly three hyphens in the delimiter row that follows a table header, allows alignment colons, and ignores fenced code and ordinary data rows.
`docs/sentence-per-line` reports clear packed sentences and incorrect continuation boundaries in paragraphs, list items, and authored GitHub alerts.
Its [detection limits](#markdown-sentence-boundaries) preserve literal quotations and syntax that cannot safely follow prose formatting.
The profile and Sinon root enable this diagnostic rule without providing automatic prose fixes.
`docs/no-em-dash` reports em dashes in the same authored prose contexts.
`docs/no-relative-skill-links` reports relative `SKILL.md` destinations in authored links and reference definitions.
Ordinary reference links and absolute URLs remain valid.
The link rule classifies external targets from the raw destination and removes query or fragment data before comparing paths.
It normalizes only ASCII unreserved percent escapes once, following [RFC 3986 Section 6.2.2.2](https://www.rfc-editor.org/rfc/rfc3986.html#section-6.2.2.2).
Reserved delimiters such as `%2F`, `%3A`, `%3F`, and `%23` stay encoded.
Control bytes, non-ASCII bytes, and malformed escapes stay literal.
HTML character references, backslash escapes, and recursive percent decoding remain outside the normalization.
The rule never rewrites links or reported source ranges.
These three authored-prose rules share `sentence-per-line.ts` so their token, quotation, and document protections stay consistent.
Sentence and punctuation checks use visible prose, while the link rule inspects destination tokens without treating them as prose.
Both new rules preserve code, frontmatter, quotations, and generated or licensed documents and provide no automatic fixes.
Sinon initially enables the new diagnostics only for Harness content, while the portable profile enables them for installed projects.
Copy that directory alongside the config so the `customRules` paths resolve.
The profile-owned `ignores` entry excludes `node_modules` even when a fresh target has no `.gitignore`.
Retain the target's existing ignore configuration when merging.
Do not copy the root-only custom rule paths from other repositories.
The Sinon repository root loads thin entry points from `rules/`.
Those entry points re-export the canonical rules from `tooling/pnpm/rules/` inside this package.
Add `markdownlint-cli2` as a development dependency and `check:markdownlint-cli2` plus `fix:markdownlint-cli2` script entries through the manifest merge above.
The `check:markdownlint-cli2` task runs the plain `markdownlint-cli2` command.
The `fix:markdownlint-cli2` task adds `--fix`.
The selected target runtime must support loading the profile's TypeScript custom rules natively.
Both resolve configuration and globs from the config file.

### Markdown Sentence Boundaries

Sentence-per-line formatting is a local authoring policy, not a Markdown syntax requirement.
Markdownlint's built-in rules do not enforce sentence boundaries.
Its custom rule API recommends Micromark tokens, which the existing profile rule already uses.
The evaluated `markdownlint-rule-max-one-sentence-per-line` package permits sentences spanning lines and provides automatic rewrites.
Those behaviors do not satisfy this profile's continuation and preservation requirements.
Extend the existing rule rather than adding a second parser, rule package, or prose rewrite path.

The rule reports `.`, `!`, or `?` followed by an uppercase or numeric sentence start on the same line.
It recognizes common abbreviations and semicolon joins using bounded English patterns.
New regular past forms come only from existing imperative verbs that end in `e`, with `d` added and `leave` and `write` excluded.
The new branch recognizes one visible candidate word after `the` when it starts with a Unicode letter or number.
The word may continue with Unicode letters, numbers, combining marks, apostrophes, or hyphens and must end before `.`, `!`, `?`, `;`, `；`, or the physical line ending.
The branch excludes `most`, `least`, `worst`, and every candidate word ending in `est` as a conservative spelling filter for degree expressions.
This filter does not prove that the remaining word is a direct object, and it misses genuine noun objects such as `request`, `test`, and `forest`.
Objects containing any opaque span receive no new recognition because masking cannot establish their word count or spelling.
Literal words containing underscores are also excluded from the additional forms.
Existing finite and imperative patterns keep their previous treatment of opaque text and degree words.
Organization suffixes such as `Inc.` and enumeration suffixes such as `etc.` can end a sentence when the following text starts a recognized independent clause or instruction.
Capitalization alone does not establish an independent clause after a semicolon.
Conjunctive adverbs must start the following clause rather than occur inside a later list item.
Colon-introduced semicolon series preserve comma-containing items before a final `and` or `or` item.
It also recognizes sentence endings inside quotations when independent prose follows the closing quotation mark.
Adjacent lines within one paragraph are checked when the previous line ends with an alphanumeric character.
A lowercase continuation indicates wrapped prose, while a recognized imperative indicates an instruction missing its terminator.
Separate paragraphs and list items are not joined for this check.

Code, frontmatter, headings, HTML blocks, links, URLs, decimal values, version strings, and tables remain protected.
Unmarked blockquotes and paired literal quotation marks preserve exact quotations, including quotations spanning source lines.
Marked GitHub alerts remain authored prose and are checked inside their blockquote containers.
Quoted contractions and escaped inner quotation marks preserve the outer quotation boundary.
Single-letter initials are recognized only in bounded personal-name contexts, not after arbitrary single-letter values.
License files named `LICENSE`, `LICENCE`, or `COPYING`, with optional extensions, are excluded from this prose rule.
Generated files are recognized by `generated: true` or `autogenerated: true` frontmatter fields.
Leading HTML comments beginning with `Generated`, `Automatically generated`, or `Auto-generated` also identify generated files within five lines.
Other generated or vendored material still needs the target's existing ignore policy.
These protections affect only the prose rule and do not disable other Markdown checks.

This check is conservative and does not parse natural-language grammar.
It cannot recognize every abbreviation, language, independent clause, unmarked quotation, or generated file.
Lowercase sentence starts, unfamiliar semicolon clauses, and capitalized wrapped noun phrases may escape detection.
Ambiguous lists, temporal expressions, abbreviation boundaries, and partly masked inline text can still produce false positives or false negatives.
Continuation checks can mistake deliberately arranged unpunctuated prose for wrapped sentences.
Review each diagnostic manually and preserve meaning when separating sentences or restoring terminators.
Passing this rule establishes only that its supported patterns were absent.

## Type-Aware Promise Checks

Install the profile's compatible `oxlint-tsgolint` development dependency alongside Ultracite.
The profile scripts pass `--type-aware` through Ultracite and the native configuration enables the built-in `typescript/no-floating-promises` rule with `ignoreVoid: false`.
The regression fixture invokes only Ultracite, proves the diagnostic disappears when type awareness is off, and proves normal fixes preserve `void` expressions.
The rule diagnoses unhandled promises rather than every use of `void`.
It accepts explicit rejection handling, awaited calls, and returned promises.
It provides suggestions upstream, but normal Ultracite fixes do not rewrite `void` into `await`.
Keep that conversion under the [common async policy](../rules.md#code-rules-common-to-all-languages) because scheduling and cancellation require semantic review.
Use the target's real TypeScript project configuration and verify type-aware execution before relying on this diagnostic.
The tested backend contract is documented in the [Oxlint rule implementation](https://github.com/oxc-project/oxc/blob/oxlint_v1.87.0/crates/oxc_linter/src/rules/typescript/no_floating_promises.rs) and the [Ultracite command implementation](https://github.com/haydenbleasel/ultracite/blob/ultracite%408.0.1/packages/cli/src/commands/check.ts).

## TypeScript Strictness Merge

`tsconfig.fragment.json` lists strictness flags that the maintainer's TypeScript projects already use.
They are `exactOptionalPropertyTypes`, `noFallthroughCasesInSwitch`, `noImplicitOverride`, `noImplicitReturns`, `noPropertyAccessFromIndexSignature`, `noUncheckedIndexedAccess`, and `verbatimModuleSyntax`.
Merge only the flags the target's `compilerOptions` do not already set, and preserve any value the target chose.
Keep the target's `extends`, `module`, `target`, `include`, and `types` settings unchanged.
Each flag can introduce new compiler errors in existing code, so run `pnpm exec tsc --noEmit` after merging and report the new errors instead of fixing unrelated source.
Skip the fragment for a JavaScript-only target.
`verbatimModuleSyntax` requires `import type` for type-only imports, which matches the profile's `typescript/consistent-type-imports` rule.

## Commands

Run `pnpm install` after a dependency change.
Run `pnpm run check` for read-only lint and formatting checks owned by the target package.
The `check:ultracite` task runs `ultracite check`, which reports findings without writing source changes.
The `check:markdownlint-cli2` task lints Markdown files at the project root without writing changes.
Run `pnpm run fix` within the authorized source-edit scope.
The `fix:ultracite` task runs the Ultracite fixer.
The `fix:markdownlint-cli2` task applies Markdownlint fixes.
Run `pnpm run test` only when the target owns that native test command.
The source profile uses `vitest run` for non-watching tests.
Omit the test command when the target has no native test owner rather than creating a dummy suite or relying on an empty-suite flag.
Run `pnpm exec tsc --noEmit` only when the target has TypeScript sources, a compiler configuration, and compiler dependencies.
Provide the selected test runner's declarations when compiler inputs include its tests.
Do not run a TypeScript compile check for a JavaScript-only target.

## Runtime And CI

Select the development runtime from the target's manifest and explicit requirements.
Check tool and custom plugin compatibility before changing the target's selected runtime.
Keep source execution requirements separate from development dependency engine requirements.

pnpm 12 is a native executable and does not require Node.js after installation.
Installing pnpm 12 through npm requires Node.js 22.13 or newer.
Bootstrap it with a compatible development Node.js version or use its standalone installer.
Pin pnpm through the target's `packageManager` field.
The GitHub catalog uses `pnpm/setup@v3.0.0` and reads that pin from the target manifest.
The catalog supplies Node.js 24 as a source example.
Replace that runtime selection when the target requires another compatible version.
It does not infer pnpm from `package.json` alone.
The GitLab catalog supplies a Node.js 24 image example and enables Corepack to use the target's `packageManager` pin.
Adapt runtime versions, working directories, and image pins to the target's existing CI requirements.
CI runs `pnpm install --frozen-lockfile` when `pnpm-lock.yaml` exists, then the target's `pnpm run check` script.
Require a committed lockfile for a frozen installation.
Replace the install mode only for an authorized dependency update job or an explicit target requirement.
CI must run `pnpm run test` only when the target has a native test owner.
CI must run `pnpm exec tsc --noEmit` only when TypeScript is selected and a target compiler configuration exists.
Existing CI jobs and package scripts take precedence over these catalog entries.

## Source Profile Version Evidence

The source profile selects pnpm `12.10.1`, Oxlint `1.87.0`, Oxfmt `0.72.0`, Ultracite `8.0.1`, Markdownlint `0.41.1`, Markdownlint CLI2 `0.23.3`, npm-run-all2 `9.0.3`, Vitest `5.0.3`, and `@oxc-project/types` `0.153.0`.
Verify Ultracite upgrades against its selected-version source and supported Oxlint, Oxfmt, and plugin APIs.
The target owns final dependency versions through its manifest and lockfile.
The fragment's catalog ranges are source-profile evidence, not mandatory target pins.
Check npm before merging them.
