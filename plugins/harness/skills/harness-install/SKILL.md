---
name: harness-install
description: Use when installing or refreshing Harness implement and review guidance in a target repository.
---

# Harness Install

Install current Harness guidance as independent project files under `.claude/skills/`.
This skill owns target mapping, conflicts, and installation proof.
Profile references own native configuration and build integration.
Do not install this installer skill into the target.

## Target And Authority

Obtain the target path and verify its repository identity, applicable host instructions, `.claude/` contents, and Git status before edits.
Inspect existing mapped paths before treating them as Harness-owned.
Preserve unrelated changes, custom skills, configuration, and hooks.
A dirty target is allowed when installation changes remain isolated.
Inspection alone grants no replacement authority.

Show the mapped-path preview before writing.
The installation request permits missing guidance files and authorized profile merges; no separate preview approval is required for those actions.
Ask before replacing conflicting content unless an explicit grant already covers the exact replacement.
A prior installation or dirty worktree is not a replacement grant.

## Profiles And References

Select profiles per independent project root, using explicit selection when detection is ambiguous.
For a polyglot root, read each applicable language and tool document once.
Do not load unselected native integrations.

| Profile | Language association | Native integration reference |
| --- | --- | --- |
| `bun` | JavaScript, TypeScript, JSX as applicable | [Bun](../../docs/tools/bun.md) |
| `gradle` | Java; Kotlin with verified Kotlin integration | [Gradle](../../docs/tools/gradle.md) |
| `maven` | Java; Kotlin only with the verified conditional classpath path | [Maven](../../docs/tools/maven.md) |
| `uv` | Python | [uv](../../docs/tools/uv.md) |
| `go` | Go | [Go](../../docs/tools/go.md) |
| `rust` | Rust | [Rust](../../docs/tools/rust.md) |
| `shell` | POSIX shell or Bash according to the target dialect | [Shell](../../docs/tools/shell.md) |

## Installed Layout

Create these paths relative to the target root:

```text
.claude/skills/
+-- implement/SKILL.md
+-- review/SKILL.md
+-- docs/
    +-- rules.md
    +-- languages/
    |   +-- language documents
    +-- tools/
        +-- tool documents
```

Harness manages exactly two target skills: `implement` and `review`.
Other existing skills remain untouched; profile documents are support material, not skills.
Copy the common rules and all eight language and seven tool documents by default to preserve the complete reference index.
Consumers load only references relevant to their task, regardless of which documents are present.
If installing selected documents only, filter the common index and include every linked companion.
Never leave a reference to a document that was not copied.

Use ordinary target-owned files, not symlinks or source-checkout dependencies.
Do not add installer manifests, caches, hash records, agents, execution-plan files, autonomous skills, or compatibility aliases.
Native tooling belongs at target configuration or CI paths, not under `.claude/skills/`.

## Source Mapping

Resolve the installed plugin root from `${CLAUDE_PLUGIN_ROOT}` or the absolute installed path supplied by the host.
Read current canonical inputs from that root, not a source checkout in the target repository.

| Target path | Canonical source |
| --- | --- |
| `.claude/skills/implement/SKILL.md` | `${CLAUDE_PLUGIN_ROOT}/skills/implement/SKILL.md` |
| `.claude/skills/review/SKILL.md` | `${CLAUDE_PLUGIN_ROOT}/skills/review/SKILL.md` |
| `.claude/skills/docs/rules.md` | `${CLAUDE_PLUGIN_ROOT}/docs/rules.md` |
| `.claude/skills/docs/languages/<name>.md` | `${CLAUDE_PLUGIN_ROOT}/docs/languages/<name>.md` |
| `.claude/skills/docs/tools/<name>.md` | `${CLAUDE_PLUGIN_ROOT}/docs/tools/<name>.md` |
| `<target-root>/.markdownlint-cli2.jsonc` | `${CLAUDE_PLUGIN_ROOT}/tooling/bun/.markdownlint-cli2.jsonc` when Bun is selected |
| `<target-tooling>/kotlin-ktlint/` | `${CLAUDE_PLUGIN_ROOT}/tooling/kotlin-ktlint/` when Kotlin is selected |

Read the two skills, common rules, and selected language and tool references before composing their target files.
Preserve canonical content except for these location changes and an explicitly filtered reference index:

- In both copied skills, rewrite the published `../../docs/rules.md` link to `../docs/rules.md`.
- Keep common-rule links to `languages/<name>.md` and `tools/<name>.md` unchanged.
- Keep language and tool links to `../rules.md`, `../languages/<name>.md`, and `../tools/<name>.md` unchanged.

The published and installed layouts differ only at the consumer-to-rules link.
Do not change published links to match the target or point target guidance back to the plugin root.
Profile-native source and destination paths follow the selected tool reference and target layout.

## Preview And Conflict Handling

Read each existing destination and preview one status per selected guidance or native path:

- `create`: destination does not exist.
- `keep`: destination bytes match the composed current input.
- `conflict`: destination differs and replacement is not authorized.
- `replace`: an explicit grant covers replacing this conflicting Harness-owned file.

For a conflict, report ownership and a diff or concise change summary; leave it unchanged until replacement is authorized.
Compose destinations from the same current canonical input set on each run.
Create missing files, leave matching files untouched, and replace only explicitly approved Harness-owned conflicts.
Do not maintain a second target-specific implementation.
Create parent directories only for mapped destinations.
Reject symlink ancestors, paths outside the target root, and file-versus-directory mismatches before writing.
Preserve the mode of an existing file when replacing it.
Never delete files merely because they are absent from the mapping.

## Native Integration

Read the target manifest, build files, editor configuration, CI, and project identity for each selected integration.
Use the profile's merge rules; add only missing settings and preserve target values on conflict.
Keep existing manifests, identity, source roots, toolchains, test owners, formatters, checkers, hooks, and unrelated CI jobs.
Do not replace whole configuration files or silently upgrade dependencies to match a profile.
Create native configuration only where the selected reference permits it and no target-owned file conflicts.

For Bun, use the [bounded manifest and Markdownlint merge](../../docs/tools/bun.md#bounded-manifest-merge).
The profile owns Markdownlint dependency/scripts and config-based `node_modules` exclusion, including targets without `.gitignore`.
For Kotlin, read and copy the complete native module, including source, resources, build files, and tests.
Use the selected Gradle or conditional Maven integration to attach its produced JAR to the actual ktlint runtime.
Do not install only a prebuilt JAR or substitute buildSrc classes for ruleset registration.
The native build graph must build the module before consumer lint runs; otherwise report the integration gap.
Do not install the module when Kotlin is unselected.

Install CI catalogs only when explicitly selected, retaining least-privilege read permissions.
Adapt working directories, versions, stages, and test ownership to the target's existing jobs.
When no test owner exists, omit that test task and report it as not applicable.

## Verification And Completion

Verify written destinations with the native filesystem:

- Mapped files are regular files within the target; custom skills and unrelated native files remain unchanged.
- Managed skill frontmatter names are `implement` and `review`, and both rules links resolve in the installed layout.
- Relative instruction links resolve within the copied guidance without plugin-root variables, checkout paths, or symlink dependencies.
- Recompose from unchanged canonical inputs; completed paths produce only `keep` statuses on the second pass.

For changed native integrations, run the target's documented affected checks and profile-specific acceptance proof.
A Kotlin integration needs the complete module, runtime registration, and consumer-lint proof required by its selected tool reference.
A Bun integration must resolve its Markdownlint configuration at the selected project root.
Do not invent check commands, add test infrastructure, or rerun unaffected profile checks for guidance-only refreshes.

Report selected profiles, root associations, mapped statuses, preserved customizations, and canonical source identity.
Use portable paths in Git or public reports; keep any necessary absolute source or target path in private task context.
Include filesystem and second-pass results, native commands and exit codes, and unresolved conflicts or gaps.
Distinguish source inspection and file-copy proof from native integration and hosted CI behavior.
Installation is complete only when requested paths and integrations are verified with no unresolved required conflicts or checks.
