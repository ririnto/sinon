---
metadata:
  reference:
    pnpm:
      url: https://pnpm.io/catalogs
    OpenAI skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
    OpenAI project instructions:
      url: https://learn.chatgpt.com/docs/agent-configuration/agents-md.md
    Rethinking Skills and Prompts for GPT-6 Astra:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
---

# Repository Conventions

Use the sections for the files under change.
Shell and TypeScript sections apply to source, documentation sections to prose, and YAML rules to configuration.

## Shell

POSIX scripts start with `#!/usr/bin/env sh`, `# -*- coding: utf-8 -*-`, and `set -e`.
Do not use `set -u`, `[[ ]]`, standalone bracket short-circuits, output suppression to `/dev/null`, or `|| true`.
Use uppercase constants, lowercase variables, function docstrings, no blank function-body lines, and visible diagnostics.

## TypeScript

For TypeScript source, use [TypeScript Conventions](typescript.md).

## Source Changes

Prefer an available AST, PSI, or parser.
Use text or regex surgery only for lexical, small, or no-parser work, and name the edited node when you do.
Preserve public declaration documentation and local comment conventions.
Use meaningful named regex groups and name-based access whenever extracting captures, including the full match.
Do not use ordinal access such as `match[0]`, `match[1]`, capture destructuring, or numbered subgroup calls.
Use named replacement references instead of positional capture references.
Keep ordinary array indexing separate from regex capture access.
Prefer an existing parser or literal operation when it expresses the extraction more clearly.
For engines without named groups, use a clearer native alternative or report the unsupported requirement.
Do not invent named-group support, add artificial wrappers, or add packages solely for this convention.
Preserve optional groups, selected matches, flags, Unicode, offsets, iteration, replacements, negative cases, evaluation order, and cleanup.
Exclude third-party, vendored, and generated code from this convention's migration scope.
Keep function bodies free of blank lines, and separate function or class declarations with one blank line.
Subject to configured lint, inline variables used only once and keep single-use logic inline.
Do not extract or retain helpers for hypothetical future reuse or test convenience.
Do not count test references as production reuse.
Preserve behavior, evaluation count and order, side effects, resource lifetime, and required production interfaces.
Preserve exception timing, mutable snapshots, capture, and overload or receiver resolution.
Retain bindings or helpers only for required production interfaces, preserved semantics, or configured lint.
State the concrete reason for each retained binding or helper.
Do not suppress, weaken, or reconfigure lint to satisfy these preferences.

Change production code only for a real product requirement or an actual defect.
Never change production code to accommodate tests, expose internals, add test hooks, or manufacture test seams.
Adapt tests to production interfaces and behavior.

Write docstrings and source documentation comments in English.
Apply these formatting rules to maintained source and code samples.
Use the language's multiline documentation form.
Keep one-sentence docstrings multiline.
When the language uses delimiters, put the opening and closing delimiters on separate lines.
State a contract or reason instead of restating the identifier.

## Documentation and Configuration

Use English headings unless an existing document intentionally differs.
Leave blank lines around headings, lists, and language-tagged fences.
Use semantic line breaks, ASCII trees, and BCP 14 terms in stable rules.
YAML uses the `.yaml` extension unless a host or tool contract requires `.yml` (for example `.gitlab-ci.yml` at a repository root).
YAML uses plain or double-quoted short scalars, folded blocks for wrapped logical strings, and literal blocks for meaningful line breaks.
YAML sequences use block style.
Keep a flow sequence only for an explicit empty sequence (`key: []`).
A block form cannot express an empty sequence without changing it to null.
Do not quote a scalar when its parsed type and value already match the consumer's need.
Use double quotes only when the actual consumer requires the exact string type or value, such as a YAML 1.1 scalar that would coerce, or a version-like or numeric-key scalar that must stay a string.

## Markdown Authoring

Prefer a body H1 unless the document relies on frontmatter for its title.
For Harness guidance and documents with a frontmatter title, keep the title only in frontmatter.
Do not add frontmatter only to replace a body H1.

Write one prose sentence per source line, including nested-list continuation lines.
Separate paragraphs with blank lines.
Do not pack multiple sentences into one line with semicolons.
Indent nested-list continuation lines to align under their item text.
Keep fenced code blocks, YAML frontmatter, tables, and link syntax intact, and never split a sentence inside code, URLs, or version strings.
Relative references that load instruction or implementation content must stay inside the same publishable plugin.
Use real `#` heading syntax for structural headings.
Do not use bold text on its own line as a pseudo-heading.
A natural prose lead-in to a code block does not need to become a heading.

## Instruction And Skill Authoring

Use [instruction authoring](instruction-authoring.md) when changing agent guidance, skills, host hooks, or model prompts.

## Dependency Selection

Before adding or upgrading a library or tool, check its registry for the latest stable version compatible with the target's runtime and dependency constraints.
Use the relevant registry, such as npm, Maven Central, PyPI, or crates.io, rather than treating a version in guidance as current.
Respect the target's lockfile, parent POM, BOM, version catalog, and existing pins.
Use the package manager's or build tool's native catalog for dependency versions when it supports catalogs.
Reference catalog entries instead of repeating version literals in consumers.
For pnpm, define catalog versions in `pnpm-workspace.yaml` and use `catalog:` references in package manifests.
Treat historical version examples as examples.
Change a target pin only when the task authorizes that dependency change.

## Official References

Read the authoritative upstream documentation before deriving reference content from it.
Prefer vendor-provided Markdown or plain-text pages for the same subject and version.
Request `Accept: text/markdown` when the publisher supports that response format at the cited URL.
Use an official `llms.txt` or documentation index to find a page when its format or location is unclear.
Cite the specific page rather than replacing it with the discovery index.
Verify the response body and preserve the topic, version, and meaningful fragment when changing a source URL.
Use upstream raw-file URLs for source citations when the exact file and ref are verified.
Retain authoritative HTML pages when no equivalent text page exists.
Keep schemas, package artifacts, registry endpoints, and executable URLs in their native formats.
Keep authoritative citations out of body prose.
Record sources for a skill and its child references once in the owning `SKILL.md` frontmatter under `metadata.reference`.
Use concise source names as keys.
For documents without an owning skill, keep source metadata in that document's frontmatter.
Omit redundant words such as `Reference` or `README` from source names.
Record its version, tag, or other appropriate identifier in a separate field when available.
Do not repeat the source name in a version value unless it belongs to the official version label.
Use a list of mappings under one source name when distinct versions have distinct URLs.
Keep each `version` or `tag` and its corresponding `url` in the same mapping.
Do not add a read date.
Use `url` with a scalar for one source page or a list for multiple pages of the same source version.
Never fabricate a commit or version.
Keep the original content faithful within the license and copyright of the source, and preserve its licensing and attribution.
Distinguish exact preservation of official source from our own authored or adapted examples: label an adapted example as adapted, and never call edited code verbatim.
