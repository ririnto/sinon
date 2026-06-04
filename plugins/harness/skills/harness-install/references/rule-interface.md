# Native Enforcement Model

Document the native-linter enforcement model: each stack uses its ecosystem's native tool (ktlint, Spotless, ruff, ultracite, shellcheck) with optional native custom rules and structural prose conventions.

## Enforcement Inventory

Each stack delegates code-style and structure detection to its native ecosystem tool(s). Structural checks that cannot be automated remain prose conventions in the installed-target contract.

### Gradle

- Validator: `./gradlew ktlintCheck` (custom ruleset family `code`, rule ids `code:<kebab>`)
- Custom rules (buildSrc): 12 rules in the `code` ruleset family: `if-statement-braces`, `import-over-fqn`, `kotlin-top-level-declaration-count`, `implicit-lambda-it`, `leading-underscore`, `unchecked-cast-suppression`, `non-null-assertion`, `multiline-doc-style`, `unstructured-logging`, `companion-object-position`, `terminal-branch-when`, `public-declaration-doc-comment`.
- EditorConfig knobs: `ktlint_unchecked_cast_suppression_forbidden`, `ktlint_unchecked_cast_suppression_allowed`, `ktlint_multiline_doc_style_mode`, `ktlint_companion_object_position_position`.
- Standard ktlint ruleset: also runs alongside custom rules.
- Script exclusion: Custom rules skip `.gradle.kts` script files.
- Check command: `./gradlew ktlintCheck`
- Format command: `./gradlew ktlintFormat`

### Maven

- Validator: `mvn verify` runs Spotless `check` through the `verify` phase (Palantir Java formatter + import order + unused import removal + trailing-whitespace hygiene), plus a `maven-antrun-plugin` execution (id `sync-git-hooks`) bound to `verify` that syncs the tracked Git hooks into the active hooks directory.
- Automated enforcement: Format-only via Spotless (no structural rule detection in Maven validator). Java code-structure detection rules are prose-only.
- Check command: `mvn verify`
- Format command: `mvn spotless:apply`

### uv

- Validator: `uv run scripts/check.py` (thin wrapper invoking `uvx --with "ruff>=0.15.15,<0.16.0" ruff check .`)
- Ruff configuration: `ruff.toml` at repository root; selects `F403` (wildcard import detection); ruff defaults otherwise.
- Custom Python AST conventions: 7 prose-only rules (leading-underscore, multiline-doc-style, unstructured-logging, public-declaration-doc-comment, unchecked-cast-suppression, triple-quote-inline-comment, mutable-collection) — no automated enforcement; documented as code conventions only.
- Format command: `uv run scripts/format.py` (thin wrapper invoking `uvx --with "ruff>=0.15.15,<0.16.0" ruff format .`)
- Quote style: ruff format uses double quotes.

### Bun

- Validator: `bun run check` (package.json script -> `sh scripts/check.sh`: syncs Git hooks, then runs `bunx ultracite check`; ultracite preset over oxlint).
- Format command: `bun run format` (-> `sh scripts/format.sh` -> `bunx ultracite fix`).
- Configuration files: `oxlint.config.ts` and `oxfmt.config.ts` (extend `ultracite/oxlint/core` and `ultracite/oxfmt`).
- Tool self-provisioning: bunx auto-provisions ultracite, oxlint, and oxfmt on first use (unpinned; the latest published versions are fetched and cached).
- Conventions: the multiline-doc-style and public-declaration-doc-comment conventions are maintained by convention on bun, not enforced by tooling (ultracite/oxlint core governs linting).

### Shell

- Validator: `sh scripts/check.sh` (wrapper invoking native `shellcheck`)
- Format command: `sh scripts/format.sh` (wrapper invoking `shfmt`)
- Configuration file: `.shellcheckrc` at repository root.
- Structural checks (prose-only): file presence, directory presence, empty-directory placeholders, hook shebangs, hook executable bits, hook command parity, CI command parity, symlink safety, scaffold-leak scanning.

## Structural Conventions (Prose-Only)

These checks are now DOCUMENT-LEVEL PROSE CONVENTIONS enforced by code review and project discipline, not automated validators. Target repositories MUST uphold these in documentation, agent instructions, and hook templates:

- filePresence: Required files (CLAUDE.md, AGENTS.md, ARCHITECTURE.md, docs/harness/README.md, etc.) MUST exist and be tracked in version control.
- directoryPresence: Required directories (.claude/agents/, .claude/skills/, docs/harness/, docs/generated/, etc.) MUST exist, optionally with .gitkeep.
- emptyDirectoryPlaceholders: Empty required directories MUST use .gitkeep to stay in version control until content exists.
- symlinkSafety: Symlinks under protected harness paths MUST be limited to documented safe links (e.g., AGENTS.md ↔ CLAUDE.md, .agents → .claude).
- agentFrontmatter: Agent .md files MUST include required `name` and `description` frontmatter fields.
- skillFrontmatter: Skill SKILL.md files MUST include required `name` frontmatter field and `description`.
- docHeadings: Documentation MUST use properly nested Markdown headings, starting at level 1, with blank lines before headings.
- docContent: Documentation MUST use appropriate fenced code blocks with language tags, blank lines before lists, and correct emphasis styles.
- scaffoldLeaks: Placeholder tokens (e.g., `{{project-name}}`, `<command>`) MUST NOT appear in committed source code; only in source templates under docs/harness/templates/.
- hookShebang: Hook files MUST use the `/usr/bin/env sh` shebang when executable.
- hookExecutable: Generated hook files MUST have executable bits set (mode 755 or `a+x`).
- hookGeneratedMarker: Generated hook files MUST include a comment indicating they are auto-generated and should not be edited directly.
- hookStage: Hook files MUST declare which stage they run (pre-commit or pre-push).
- hookCommand: Generated hooks MUST invoke the correct stack-specific validation or final-check command (Gradle: ktlintCheck for pre-commit, check for pre-push; non-Gradle: stack-specific command).
- ciHookCommandParity: The `.github/workflows/<tool>.yaml` and `.gitlab-ci.yml` files MUST run the same final-check command as the generated pre-push hook.
- envShebangUsage: Shell scripts MUST use the `/usr/bin/env` shebang pattern rather than direct interpreters.
- uncheckedTasks: Completed execution plans under docs/exec-plans/completed/ MUST NOT contain any unchecked `- [ ]` task items.
- templateGroups: Templates under docs/harness/templates/ MUST match the installed template structure and renderable variable names used by the installer.
