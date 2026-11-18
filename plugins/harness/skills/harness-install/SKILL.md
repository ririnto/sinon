---
name: harness-install
description: Use when installing or refreshing Harness implement and review guidance in a target repository.
metadata:
  reference:
    OpenAI instruction design:
      url: https://developers.openai.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra.md
    OpenAI skills:
      url: https://learn.chatgpt.com/docs/build-skills.md
---

# Harness Install

Install current Harness guidance as independent project files under the target host's skill root.
This skill owns target mapping, conflicts, and installation proof.
Profile references own native configuration and build integration.
Do not install this skill into the target.

## Target And Authority

Before edits, verify the target path, repository identity, host instructions, skill root contents, and Git status.
Use the [project baseline and fresh-root setup](../../docs/tools/baseline.md) before selecting native integrations.
Inspect existing mapped paths before treating them as Harness-owned.
Preserve unrelated changes, custom skills, configuration, and hooks.
A dirty target is allowed when installation changes remain isolated.
Inspection alone grants no replacement authority.

Show the mapped-path preview before writing.
The installation request permits missing guidance files and authorized profile merges.
No separate preview approval is required for those actions.
Ask before replacing conflicting content unless an explicit grant already covers the exact replacement.
Continue independent authorized mappings while a conflicting destination awaits a decision.

## Profiles And References

Select profiles per independent project root, using explicit selection when detection is ambiguous.
For a polyglot root, read each applicable language and tool document once.
Do not load unselected native integrations.

| Profile | Language association | Native integration reference |
| --- | --- | --- |
| `pnpm` | JavaScript, TypeScript, JSX as applicable | [pnpm](../../docs/tools/pnpm.md) |
| `gradle` | Java; Kotlin with verified Kotlin integration | [Gradle](../../docs/tools/gradle.md) |
| `maven` | Java; Kotlin only with the verified conditional classpath path | [Maven](../../docs/tools/maven.md) |
| `uv` | Python | [uv](../../docs/tools/uv.md) |
| `go` | Go | [Go](../../docs/tools/go.md) |
| `rust` | Rust | [Rust](../../docs/tools/rust.md) |
| `shell` | POSIX shell or Bash according to the target dialect | [Shell](../../docs/tools/shell.md) |

## Installed Layout

Use `.claude/skills/` for Claude Code or `.agents/skills/` for Codex.
Select the host before previewing.
Use `<skill-root>` for that selected path throughout the mapping.
Under `<skill-root>/`, create `implement/SKILL.md`, `review/SKILL.md`, `docs/rules.md`, and the documents in `docs/languages/` and `docs/tools/`.
Harness manages only the `implement` and `review` skills; leave other skills untouched.
Profile documents are support material, not skills.
Copy the common rules and all language and tool documents by default to preserve the complete reference index.
Consumers load only references relevant to their task, regardless of which documents are present.
If installing selected documents only, filter the common index and include every linked companion.
Never leave a reference to a document that was not copied.

Use ordinary target-owned files, not symlinks or source-checkout dependencies.
Do not add installer manifests, caches, hash records, agents, execution-plan files, autonomous skills, or compatibility aliases.
Native tooling belongs at target configuration or CI paths, outside the selected skill root.

## Source Mapping

Resolve `<plugin-root>` two levels above this skill's directory from its absolute path in the host's skill listing, or use `CLAUDE_PLUGIN_ROOT` in Claude Code.
Read current canonical inputs there, not from a checkout in the target.
Before previewing or performing baseline setup or native integration, read [native source mapping and integration](./references/native-integration.md).

| Target path | Canonical source |
| --- | --- |
| `<skill-root>/implement/SKILL.md` | `<plugin-root>/skills/implement/SKILL.md` |
| `<skill-root>/review/SKILL.md` | `<plugin-root>/skills/review/SKILL.md` |
| `<skill-root>/docs/rules.md` | `<plugin-root>/docs/rules.md` |
| `<skill-root>/docs/languages/<name>.md` | `<plugin-root>/docs/languages/<name>.md` |
| `<skill-root>/docs/tools/<name>.md` | `<plugin-root>/docs/tools/<name>.md` |

Read the two skills, common rules, and selected language and tool references before composing their target files.
Preserve canonical content except for these location changes, the profile-specific hook adaptation, and an explicitly filtered reference index:

- In both copied skills, rewrite the published `../../docs/rules.md` link to `../docs/rules.md`.
- Keep common-rule links to `languages/<name>.md` and `tools/<name>.md` unchanged.
- Keep language and tool links to `../rules.md`, `../languages/<name>.md`, and `../tools/<name>.md` unchanged.

Do not change published links to match the target or point target guidance back to the plugin root.
Profile-native source and destination paths follow the selected tool reference and target layout.

## Preview And Conflict Handling

Read each existing destination and preview one status per selected guidance or native path:

- `create`: destination does not exist.
- `keep`: destination already satisfies the composed input or authorized merge.
- `merge`: the selected profile permits a bounded change that preserves target-owned content.
- `conflict`: destination differs and replacement is not authorized.
- `replace`: an explicit grant covers replacing this conflicting Harness-owned file.

For a merge, preview ownership and proposed additions; the request authorizes only the bounded profile merge, not replacement of target-owned content.
For a conflict, report ownership and a diff or concise summary, and leave it unchanged until replacement is authorized.
Compose from the same current canonical inputs on each run: create missing files, apply authorized merges, keep satisfied files, and replace only explicitly approved Harness-owned conflicts.
Do not maintain a second target-specific implementation.
Create parent directories only for mapped destinations.
Reject symlink ancestors, paths outside the target root, and file-versus-directory mismatches before writing.
Preserve the mode of an existing file when replacing it.
Never delete files merely because they are absent from the mapping.

## Native Integration

Apply the selected profile's bounded merge rules, preserve target-owned files and settings, and never silently upgrade dependencies.

## Verification And Completion

Verify written destinations with the native filesystem:

- Mapped files are regular files within the target.
  Custom skills and unrelated native files remain unchanged.
- Managed skill frontmatter names are `implement` and `review`, and both rules links resolve in the installed layout.
- Relative instruction links resolve within the copied guidance without plugin-root variables, checkout paths, or symlink dependencies.
- Recompose from unchanged canonical inputs.
  Completed paths produce only `keep` statuses on the second pass.

For changed native integrations, run the target's documented affected checks and profile-specific acceptance proof.
Do not invent check commands, add test infrastructure, or rerun unaffected profile checks for guidance-only refreshes.

Report profiles, root associations, mapped statuses, preserved customizations, canonical source identity, filesystem and second-pass results, native commands and exit codes, and unresolved conflicts or gaps.
Use portable paths in Git or public reports; keep necessary absolute paths in private task context.
Distinguish source inspection and file-copy proof from native integration and hosted CI behavior.
Complete installation only when all requested paths and integrations are verified without unresolved required conflicts or checks.
