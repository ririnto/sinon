---
title: Lint Rule Mapping
metadata:
  reference:
    Oxlint:
      url:
        - https://oxc.rs/docs/guide/usage/linter/rules.html
        - https://oxc.rs/docs/guide/usage/linter/js-plugins.html
        - https://oxc.rs/docs/guide/usage/linter/rules/react/set-state-in-render.html
        - https://oxc.rs/docs/guide/usage/linter/rules/oxc/no-accumulating-spread.html
        - https://oxc.rs/docs/guide/usage/linter/rules/eslint/no-inline-comments.html
    React lint rules:
      version: 7.1.1
      url:
        - https://react.dev/reference/eslint-plugin-react-hooks
        - https://react.dev/reference/eslint-plugin-react-hooks/lints/set-state-in-effect
        - https://react.dev/reference/eslint-plugin-react-hooks/lints/set-state-in-render
        - https://react.dev/reference/eslint-plugin-react-hooks/lints/rules-of-hooks
    Stylistic:
      url:
        - https://eslint.style/rules/no-multiple-empty-lines
        - https://eslint.style/rules/padding-line-between-statements
    Vitest padding:
      url: https://raw.githubusercontent.com/vitest-dev/eslint-plugin-vitest/main/src/utils/padding.ts
    JSDoc:
      url: https://raw.githubusercontent.com/gajus/eslint-plugin-jsdoc/main/docs/rules/require-jsdoc.md
    TSDoc:
      url: https://raw.githubusercontent.com/microsoft/tsdoc/main/eslint-plugin/README.md
    Markdownlint:
      version: 0.42.0
      url:
        - https://raw.githubusercontent.com/DavidAnson/markdownlint/v0.42.0/lib/md060.mjs
        - https://raw.githubusercontent.com/DavidAnson/markdownlint/v0.42.0/doc/CustomRules.md
    Search-replace:
      version: 1.2.0
      url: https://raw.githubusercontent.com/OnkarRuikar/markdownlint-rule-search-replace/main/README.md
    SemBr:
      version: 0.2.0
      url: https://registry.npmjs.org/markdownlint-rule-sembr/0.2.0
    Maximum one sentence:
      version: 0.0.2
      url: https://registry.npmjs.org/markdownlint-rule-max-one-sentence-per-line/0.0.2
    Relative links:
      url: https://raw.githubusercontent.com/theoludwig/markdownlint-rule-relative-links/main/README.md
    KtLint:
      version: 1.8.0
      url:
        - https://raw.githubusercontent.com/ktlint/ktlint/1.8.0/documentation/release-latest/docs/rules/standard.md
        - https://raw.githubusercontent.com/ktlint/ktlint/1.8.0/documentation/release-latest/docs/rules/experimental.md
    Detekt:
      version: 1.23.8
      url:
        - https://detekt.dev/docs/1.23.8/rules/comments/
        - https://detekt.dev/docs/1.23.8/rules/style/
        - https://detekt.dev/docs/1.23.8/rules/potential-bugs/
        - https://detekt.dev/docs/1.23.8/rules/libraries/
        - https://detekt.dev/docs/1.23.8/rules/naming/
        - https://raw.githubusercontent.com/detekt/detekt/v1.23.8/detekt-rules-documentation/src/main/kotlin/io/gitlab/arturbosch/detekt/rules/documentation/UndocumentedPublicProperty.kt
        - https://raw.githubusercontent.com/detekt/detekt/v1.23.8/detekt-rules-style/src/main/kotlin/io/gitlab/arturbosch/detekt/rules/style/ForbiddenSuppress.kt
---

Prefer maintained upstream rules when their supported configuration preserves the required semantics.
The tables record repository policy decisions against the reviewed candidates, not a claim about every available package.
Keep custom rules when upstream changes detection boundaries, protected syntax, or required safe fixes.
No additional Detekt runtime or ESLint process accompanies these decisions.
Pillars identify intended benefits and do not imply that a rule directly measures them.
An effective command enforces a row only in targets that materialize the owning profile and run that command.

## Oxlint Policies

Each retained module remains self-contained and independently registrable through Oxlint `jsPlugins`.
The style rules use separate configurations because their modules share the canonical `style` namespace.
The existing base configuration retains its registrations.

| Custom ID | Upstream comparison | Decision and distinguishing behavior | Pillars | Effective enforcement |
| --- | --- | --- | --- | --- |
| `style/no-blank-lines-in-functions` | Stylistic empty-line, block-padding, and statement-padding rules | Retain because physical gaps inside function expressions and proven-registration exceptions differ from global or statement-level padding. | Consistency, maintainability | `ultracite:check:style:no-blank-lines-in-functions` runs its Oxlint config and `check:ultracite` composes that command. |
| `style/no-inline-comments-in-functions` | Native `eslint/no-inline-comments` and `eslint-plugin-no-comments` | Retain because standalone explanatory comments, declaration attachment, directives, and licenses require different predicates without comment-removal fixes. | Consistency, maintainability | `ultracite:check:style:no-inline-comments-in-functions` runs its Oxlint config and `check:ultracite` composes that command. |
| `style/sibling-test-spacing` | Native Jest padding and Vitest or Jest `padding-around-all` | Retain because exactly one blank line between adjacent proven registrations differs from generic padding around calls. | Consistency, maintainability | `ultracite:check:style:sibling-test-spacing` runs its Oxlint config and `check:ultracite` composes that command. |
| `tsdoc/require-export-tsdoc` | JSDoc `require-jsdoc` and `multiline-blocks`, plus TSDoc `syntax` | Retain because export statements, effective class visibility, and every preceding block's delimiters exceed those rules' contracts. | Stability, maintainability | `ultracite:check:base` runs the base Oxlint config and `check:ultracite` composes that command. |
| `react-effects/no-direct-use-effect` | React Compiler rules, including `set-state-in-effect` | Author a diagnostic-only rule because upstream accepts legitimate effects and does not prohibit every direct React effect call. | Stability, maintainability | `ultracite:check:base` runs the base Oxlint config and `check:ultracite` composes that command. |

The base configuration registers `eslint-plugin-react-hooks` through `jsPlugins` with `name: "react-hooks-js"` and `specifier: "eslint-plugin-react-hooks"`.
The base Oxlint configuration executes the adopted upstream rules and native spread rule through `ultracite:check:base`.
The five custom rules run through the base or dedicated Oxlint configurations named in the table.
The profile declares the upstream package dependency and catalog entry without introducing an ESLint process.

| Adopted upstream ID | Positive fixture | Negative fixture | Pillars | Effective enforcement |
| --- | --- | --- | --- | --- |
| `react-hooks-js/set-state-in-render` | The unconditional setter in `render.tsx` produces the aliased diagnostic. | The event-handler setter in `valid-render.tsx` produces no matching diagnostic. | Stability, maintainability | `ultracite:check:base` runs the base Oxlint config and `check:ultracite` composes that command. |
| `react-hooks-js/set-state-in-effect` | The synchronous setter in `effect.tsx` produces the aliased diagnostic. | The timer callback in `valid-effect.tsx` produces no matching diagnostic. | Stability, maintainability | `ultracite:check:base` runs the base Oxlint config and `check:ultracite` composes that command. |
| `react-hooks-js/immutability` | The prop mutation in `mutation.tsx` produces the aliased diagnostic. | The local copy mutation in `valid-mutation.tsx` produces no matching diagnostic. | Stability, maintainability | `ultracite:check:base` runs the base Oxlint config and `check:ultracite` composes that command. |
| `react-hooks-js/purity` | The render-time random call in `random.tsx` produces the aliased diagnostic. | The constant expression in `valid-random.tsx` produces no matching diagnostic. | Stability, maintainability | `ultracite:check:base` runs the base Oxlint config and `check:ultracite` composes that command. |
| `react-hooks-js/rules-of-hooks` | The conditional hook in `hooks.tsx` produces the aliased diagnostic. | The top-level hook in `valid-hooks.tsx` produces no matching diagnostic. | Stability, maintainability | `ultracite:check:base` runs the base Oxlint config and `check:ultracite` composes that command. |
| `oxc/no-accumulating-spread` | The reducer spread in `spread.ts` produces the native diagnostic. | Ordinary copying and in-place accumulation in `valid-spread.ts` produce no matching diagnostic. | Performance, maintainability | `ultracite:check:base` runs the base Oxlint config and `check:ultracite` composes that command. |

The suite invokes these fixtures through actual Ultracite with both base configurations.
The render-state rule satisfies the render-state requirement within its documented compiler coverage, including supported conditional-update exceptions.
These observations do not prove absence of every possible state update or mutation.
The reviewed `refs` and `static-components` rules complement effect analysis but are outside this six-rule adoption.
Upstream React Hooks coverage satisfies the protection goal without requiring complete direct-effect-ban deployment across the portfolio.
The custom effect module retains its chosen source-local boundary without new coverage extensions.
The adopted standards reside in lint configuration instead of duplicate guidance instructions.

## Markdown Policies

| Previous custom ID | Upstream comparison | Decision and distinguishing behavior | Pillars | Effective enforcement |
| --- | --- | --- | --- | --- |
| `docs/no-box-drawing` | Configured `markdownlint-rule-search-replace` | Replace with `search-replace` because the configured pattern preserves the first box character per line, UTF-16 ranges, code coverage, and diagnostic-only behavior. | Consistency, maintainability | `check:markdownlint-cli2` runs the configured search-replace rule in root and materialized pnpm profiles. |
| `docs/table-separators` | Built-in `MD060/table-column-style` | Retain because compact table spacing permits delimiter cells longer than three hyphens. | Consistency, maintainability | `check:markdownlint-cli2` runs the configured table rule in root and materialized pnpm profiles. |
| `docs/sentence-per-line` | SemBr and maximum-one-sentence packages | Retain because wrapped continuations, bounded semicolon clauses, quotations, alerts, and document protections differ. | Consistency, maintainability | `check:markdownlint-cli2` runs the canonical sentence rule in root and materialized pnpm profiles. |
| `docs/no-em-dash` | Text-scoped search-replace and extended-ASCII restrictions | Retain because authored-prose protections permit quotations, headings, and unrelated Unicode. | Consistency, maintainability | The root config scopes this rule to `plugins/harness/`, while the materialized pnpm profile applies it without that path restriction. |
| `docs/no-relative-skill-links` | `markdownlint-rule-relative-links` | Retain because the policy prohibits selected relative skill destinations even when those files exist. | Consistency, maintainability | The root config scopes this rule to `plugins/harness/`, while the materialized pnpm profile applies it without that path restriction. |

The replacement changes the diagnostic identifier and upstream message wrapper.
Remove the old configuration key, local implementation, and root re-export together.
Load `markdownlint-rule-search-replace` by package name in `customRules` and retain the supplied `search-replace` settings.
Its negative lookbehind limits reporting to the first matching character on each line.
A plain global character-class search would emit additional diagnostics and would not preserve the contract.
Use `searchScope: "all"` and `replace: null` to retain code coverage without automatic edits.
The root dependency and every materialized native profile declare the package through the shared catalog.

The maintained CLI fixtures cover all 128 characters, adjacent allowed characters, astral-prefix columns, fenced code, and unchanged fix output.
Markdownlint CLI2 supplies the legacy parser required by this upstream package.
Direct Markdownlint API consumers must supply their own supported parser factory rather than assuming CLI integration applies.

## Kotlin Policies

These existing rules remain attached to the current KtLint ruleset.
The comparisons do not authorize adding Kotlin enforcement through Oxlint or introducing a Detekt pipeline.
The table covers all 26 registered custom IDs.

| Custom ID | Upstream comparison | Retained difference | Pillars |
| --- | --- | --- | --- |
| `harness:companion-object-position` | Detekt `ClassOrdering` | The policy places companions first after enum entries instead of last. | Consistency, maintainability |
| `harness:comparison-direction` | Standard KtLint and Detekt catalogs | The policy diagnoses every greater-than comparison without unsafe operand-swapping fixes. | Consistency, maintainability |
| `harness:control-flow-braces` | KtLint bracing rules and Detekt mandatory-braces rules | The policy includes every single-line branch and loop body, including do-while bodies. | Stability, consistency, maintainability |
| `harness:explicit-function-return-type` | Detekt `LibraryCodeMustSpecifyReturnType` | The policy includes private and local expression-bodied named functions instead of public library APIs alone. | Stability, maintainability |
| `harness:explicit-property-type` | Detekt `LibraryCodeMustSpecifyReturnType` | The policy includes private nonlocal properties instead of public library APIs alone. | Stability, maintainability |
| `harness:explicit-unit-branch` | Detekt `ImplicitUnitReturnType` | The policy concerns branch results rather than inferred function return types. | Stability, consistency |
| `harness:function-body-blank-lines` | KtLint `no-consecutive-blank-lines` | The policy rejects single interior blank lines with structural protections. | Consistency, maintainability |
| `harness:implicit-lambda-it` | Detekt explicit-it and multiline-lambda rules | The policy diagnoses referenced implicit parameters across supported lambdas. | Consistency, maintainability |
| `harness:import-over-fqn` | Detekt `UnnecessaryFullyQualifiedName` | The prerelease candidate uses resolved class and call symbols rather than collision-free type references alone. | Consistency, maintainability |
| `harness:kotest-sibling-spacing` | Generic KtLint blank-line rules | The policy identifies proven Kotest sibling test and lifecycle registrations. | Consistency, maintainability |
| `harness:kotest-test-assertions` | Detekt check and require idioms | The policy targets test assertions and Kotest matcher alternatives. | Stability, maintainability |
| `harness:kotlin-top-level-declaration-count` | KtLint `filename` and Detekt `MatchingDeclarationName` | The policy counts top-level types with private-helper and main-function exceptions. | Consistency, maintainability |
| `harness:leading-underscore` | KtLint property naming and Detekt naming rules | The policy also rejects underscore-prefixed private backing properties. | Consistency |
| `harness:multiline-kdoc` | KtLint `kdoc-wrapping` | The policy requires multiline delimiters and reports Unicode box-drawing characters in KDoc. | Consistency, maintainability |
| `harness:nested-data-class-last` | Detekt `ClassOrdering` | The policy specifically places nested data classes after other members. | Consistency, maintainability |
| `harness:no-import-alias` | Standard KtLint and Detekt catalogs | The policy rejects aliases repeating the imported simple name rather than banning distinct aliases. | Consistency, maintainability |
| `harness:no-java-path-api` | Detekt `ForbiddenMethodCall` | The policy distinguishes Path and Files syntax and supported toFile chains while recommending Kotlin path APIs. | Stability, consistency, maintainability |
| `harness:no-line-comment` | Detekt `ForbiddenComment` | Token distinctions and KDoc, directive, shebang, and license exemptions exceed text-pattern matching. | Consistency, maintainability |
| `harness:non-null-assertion` | Detekt unsafe-call and unnecessary-not-null rules | The policy diagnoses every non-null assertion token regardless of resolved type. | Stability |
| `harness:null-comparison-identity` | Detekt null-equality rules | The policy targets equality operators against null literals rather than equals calls or referential equality. | Stability |
| `harness:public-declaration-doc-comment` | Detekt undocumented public class, function, and property rules | The visitor checks effective public and protected classes, functions, properties, primary-constructor properties, and named non-companion objects; it excludes companion objects and does not visit individual enum entries. | Stability, maintainability |
| `harness:no-regex-constructor` | Detekt `StringShouldBeRawString` | The policy prefers toRegex calls rather than checking string escaping. | Consistency, maintainability |
| `harness:slf-direct-logging` | Detekt `ForbiddenMethodCall` | Syntax-proven logging receivers and alias exclusions differ from resolved method signatures and callable references. | Stability, maintainability |
| `harness:terminal-branch-when` | Detekt `UseIfInsteadOfWhen` | The policy recommends the opposite conversion for stable parameter comparison chains. | Consistency, maintainability |
| `harness:unchecked-cast-suppression` | Detekt `ForbiddenSuppress` | Collection arguments and conditional safe annotation removal differ from upstream reporting without fixes. | Stability, maintainability |
| `harness:unstructured-logging` | Detekt `ForbiddenMethodCall` | System output chains and printStackTrace coverage differ from configured method signatures without overbroad PrintStream bans. | Stability, maintainability |

Static source comparisons establish these differences without claiming newly executed Kotlin builds or upstream fixture runs.
Keep existing Kotlin consumer implementations until their owner verifies a behavior-preserving migration.
Each Kotlin row is enforced only when a target attaches the custom ruleset JAR and runs `ktlintCheck`.
Selecting the Kotlin profile does not attach this JAR automatically.

## Cargo Toolchain Policies

The surveyed Cargo configurations vary in editions, MSRV declarations, toolchain pins, and lint policy.
The shared profile preserves target toolchains and compatibility declarations while composing the existing Clippy and rustfmt checks.
Package-specific Clippy rules beyond the shared profile thresholds and Cargo-deny policies remain target-owned.
This composition adds no custom Cargo diagnostic for shared enforcement.

| Area | Group decision | Pillars | Effective enforcement |
| --- | --- | --- | --- |
| Toolchain | Preserve target channel pins and use the stable fallback for new unpinned roots. | Stability, consistency | `rust-toolchain.toml` and the CI catalog select the target channel or stable fallback. |
| Library MSRV | Record the oldest stable compiler from the preceding twelve months in new published libraries unless their documented consumer contract requires an older floor. | Stability, consistency | `Cargo.toml` records the floor; CI tests it only when a job selects that compiler. |
| MSRV changes | Release an increase that drops an advertised compiler as a minor version. | Stability, consistency | Package release policy owns version changes because the profile does not set package versions. |
| Clippy | Keep profile thresholds at 25 for cognitive complexity, 5 for arguments, and 250 for type complexity. | Maintainability, stability | `clippy.toml` sets all three, and the workspace command denies cognitive complexity explicitly and all warnings, including `clippy::too_many_arguments`. |
| Formatting | Merge stable rustfmt settings and validate without source rewrites. | Consistency, maintainability | `rustfmt.toml` supplies the profile keys, and `cargo fmt --all -- --check` checks without writing files. |

The [Rust tool reference](rust.md) documents the support window, configuration sources, and commands.

## Excluded Portfolio Candidates

New Go and Kotlin diagnostics cannot use Oxlint's JavaScript and TypeScript AST or its supported JavaScript-plugin API.
Do not add subprocesses, custom parser wrappers, or another linter to bypass this boundary.
The existing native Kotlin rules above remain available without a new enforcement mechanism.

| Candidate | Disposition |
| --- | --- |
| Subject-taking Boolean `when` branches | Keep policy-only until semantic positive and negative fixtures and a complete current-Kotlin scan establish a reliable AST-aware rule without false positives. |
| Combined Go spacing and documentation analyzer. | Leave the combined Go analyzer unchanged because its Go AST, type information, and protected syntax need a native Go pipeline. |
| Go immediate-return inlining candidate. | Exclude portable autofix claims because its evaluation and type guards require separate native verification. |
| Go value-range checker. | Leave its diagnostic-only Go command unchanged because range classification requires Go types and platform-aware package loading. |
| Heuristic sensitive-content rule. | Exclude generic migration because heuristic secret patterns do not establish reliable secret detection. |
| Repository documentation allowlists and command wrappers. | Keep local ownership because their path inventories and execution contracts are repository-specific. |
| Nullable-Elvis-return candidate. | Exclude generalization because syntax alone does not prove nullable semantics. |

The portfolio inspection supplies source evidence and candidate exclusions, not execution proof for untouched consumers.
