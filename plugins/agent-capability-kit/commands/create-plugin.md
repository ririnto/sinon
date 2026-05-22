---
description: >-
  Create a complete Claude Code plugin from design to implementation. Use when building a new plugin root with components, manifest, and verification.
argument-hint: Optional plugin description
allowed-tools:
  - Read
  - Write
  - Grep
  - Glob
  - Bash
  - TodoWrite
  - AskUserQuestion
  - Skill
  - Task
---

# Plugin Creation Workflow

Guide the user through creating a complete, high-quality Claude Code plugin from initial concept to tested implementation. Follow a systematic approach: understand requirements, design components, clarify details, implement following best practices, validate, and test. All work follows sinon marketplace conventions, including manifest rules, skill authoring standards, and repository layout requirements.

Initial request: $ARGUMENTS

---

## Phase 1: Discovery

### Goal

Understand the plugin purpose and target users.

### Actions

1. Create todo list with all 8 phases
2. If plugin purpose is clear from arguments:
   - Summarize understanding
   - Identify plugin type (integration, workflow, analysis, toolkit, etc.)
3. If plugin purpose is unclear, ask user:
   - What problem does this plugin solve?
   - Who will use it and when?
   - What should it do?
   - Any similar plugins to reference?
4. Summarize understanding and confirm with user before proceeding

### Output

Confirmed plugin purpose statement

---

## Phase 2: Component Planning

### Goal

Determine needed components and their purposes.

### Actions

1. Load plugin-authoring skill using Skill tool to understand sinon component types and manifest rules
2. Analyze plugin requirements and determine needed components:
   - `Skills` — Specialized knowledge OR user-initiated actions (deploy, configure, analyze)
   - `Agents` — Autonomous tasks? (validation, generation, analysis)
   - `Hooks` — Event-driven automation? (validation, notifications)
   - `MCP` — External service integration? (databases, APIs)
   - `Settings` — User configuration needs?
   - `Commands` — Slash-command surface for user-invoked workflows

3. For each component type needed, identify:
   - How many of each type
   - What each one does
   - Rough triggering/usage patterns

4. Present component plan to user as table:

   ```markdown
   | Component Type | Count | Purpose |
   | --- | --- | --- |
   | Skills | 5 | Hook patterns, MCP usage, deploy, configure, validate |
   | Agents | 1 | Autonomous validation |
   | Hooks | 0 | Not needed |
   | MCP | 1 | Database integration |
   ```

5. Get user confirmation or adjustments

---

## Phase 3: Detailed Design & Clarifying Questions

### Goal

Specify each component in detail and resolve all ambiguities (CRITICAL: do not skip).

### Actions

1. For each component in the plan, identify underspecified aspects:
   - `Skills` — What triggers them? What knowledge do they provide? How detailed? For user-invoked skills: what arguments, what tools, interactive or automated?
   - `Agents` — When to trigger (proactive/reactive)? What tools? Output format?
   - `Hooks` — Which events? Prompt or command based? Validation criteria?
   - `MCP` — What server type? Authentication? Which tools?
   - `Settings` — What fields? Required vs optional? Defaults?

2. Present all questions to user in organized sections (one section per component type)

3. Wait for answers before proceeding to implementation

4. If user says "whatever you think is best", provide specific recommendations and get explicit confirmation

### Output

Detailed component specifications

---

## Phase 4: Plugin Structure Creation

### Goal

Create plugin directory structure and manifest following sinon rules.

### Actions

1. Determine plugin name (kebab-case, descriptive)
2. Choose plugin location
3. Create directory structure using bash:

   ```
   mkdir -p plugin-name/.claude-plugin
   mkdir -p plugin-name/skills/<skill-name>   # one dir per skill, each with a SKILL.md
   mkdir -p plugin-name/agents                # if agents are needed
   mkdir -p plugin-name/commands              # if the plugin ships slash commands
   ```

4. Create `.claude-plugin/plugin.json` manifest with sinon-compliant structure:
   - Include only `$schema`, `name`, `description`, `author` (object form), and component keys matching the planned directories
   - Each component key (`commands`, `skills`, `hooks`, `mcpServers`, `lspServers`, `settings`) MUST point to an existing file or directory
   - NO `version`, `agents`, or `interface` keys
5. Create `README.md` describing plugin purpose, included skills, agents, runtime model, and layout
6. Create `.gitignore` if needed (for `.claude/*.local.md`, etc.)
7. Update TodoWrite with structure creation status

### Manifest Rules

(MUST follow)

- `$schema` MUST be `"https://anthropic.com/claude-code/plugin.schema.json"`
- `author` MUST use object form: `{ "name": "handle" }`
- `skills` MUST use directory form: `"./skills/"` (with trailing slash); array-of-paths form is prohibited
- `commands` MUST use directory form `"./commands/"` (with trailing slash) when the plugin ships slash commands; array-of-paths form is prohibited
- File-typed manifest keys MUST point to exact filenames: `"hooks": "./hooks/hooks.json"`, `"mcpServers": "./.mcp.json"`, `"lspServers": "./.lsp.json"`, `"settings": "./settings.json"`
- `version` MUST NOT appear in plugin.json
- `agents` key MUST NOT appear (ship `agents/` directory at plugin root, documented in README)
- `interface` key MUST NOT appear

### Output

Plugin directory structure and manifest ready

---

## Phase 5: Component Implementation

### Goal

Create each component following sinon best practices.

### Before Implementation

Load the relevant authoring skill (skill-authoring, command-authoring, agent-authoring, hook-authoring, mcp-integration, or plugin-settings) using Skill tool. Do not preload all six.

### Actions for Each Component

### For Skills

1. For each skill:
   - Ask user for concrete usage examples
   - Plan resources (scripts/, references/, examples/)
   - Create skill directory: `skills/<skill-name>/`
   - Write `SKILL.md` with:
     - `name` field matching directory basename
     - Activation surface, common-case workflow, decision points
     - Representatives templates, copy-adaptable examples
     - Scope boundaries and first safe commands
   - Create reference files for additive depth (not common-path content)
   - Create example files for working code
   - Create utility scripts if needed

### For Agents

1. For each agent:
   - Create agent markdown file at `agents/<name>.md`
   - Agent frontmatter `name` MUST match file basename
   - Include clear description, triggering conditions, system prompt
   - Add appropriate model, color, and tools
   - Validate agent structure

### For Commands

1. For each command:
   - Write command markdown with YAML frontmatter
   - Include `description`, `argument-hint`, `allowed-tools`
   - Write instructions FOR Claude (not TO user)
   - Use `$ARGUMENTS` variable

### For Hooks

1. For each hook:
   - Create hooks.json with hook configuration
   - Prefer prompt-based hooks for complex logic
   - Use `${CLAUDE_PLUGIN_ROOT}` for portability

### For MCP

1. Create `.mcp.json` configuration with:
   - Server type (stdio for local, SSE for hosted)
   - Command and args (with `${CLAUDE_PLUGIN_ROOT}`)
   - Environment variables as needed
2. Document required env vars in README

### For Settings

1. Create settings template in README
2. Create example `.claude/plugin-name.local.md` file
3. Add to `.gitignore`: `.claude/*.local.md`

### Progress Tracking

Update todos as each component is completed

### Output

All components implemented per specifications

---

## Phase 6: Validation

### Goal

Verify plugin meets sinon and Claude Code standards.

### Actions

1. Verify manifest rules:
   - `$schema` is `"https://anthropic.com/claude-code/plugin.schema.json"` ✓
   - `author` is object form (not string) ✓
   - `skills` uses `"./skills/"` with trailing slash ✓
   - `commands` (if present) uses `"./commands/"` with trailing slash ✓
   - NO `version` field ✓
   - NO `agents` key ✓
   - NO `interface` key ✓

2. Verify directory structure and bidirectional consistency:
   - Manifest → Filesystem: All declared paths (e.g., `"./skills/"`, `"./commands/"`, `"./.lsp.json"`) exist and match declared form (directory form with trailing slash, or exact filename)
   - Filesystem → Manifest: Every plugin-root config file (`.lsp.json`, `.mcp.json`, `hooks/hooks.json`, `settings.json`) is declared in manifest with correct key and path
   - `.claude-plugin/` contains only `plugin.json`
   - `agents/` exists at plugin root (if agents present) and is NOT in manifest
   - Each agent file basename matches `name` frontmatter field (kebab-case)

3. Verify skill structure (if skills present):
   - Each skill directory has `SKILL.md`
   - `SKILL.md` contains `name` field matching directory basename
   - Progressive disclosure: common-case in `SKILL.md`, additive depth in `references/`
   - No duplicate content between `SKILL.md` and `references/`

4. Verify README completeness:
   - Plugin purpose clearly stated
   - List of included skills and agents
   - Runtime model and layout explained
   - Scope notes and key decisions documented
   - Any setup/configuration instructions provided

5. Present findings:
   - Summary of validation results
   - Any structural issues found
   - Overall compliance with sinon and Claude Code standards

6. Present validation results and ask user for approval to fix any issues

### Output

Plugin validated

---

## Phase 7: Testing & Verification

### Goal

Test plugin functionality in Claude Code.

### Actions

1. Installation instructions:
   - Show user how to test locally:

     ```
     claude --plugin-dir /absolute/path/to/plugin-name
     ```

2. Verification checklist:
   - Skills load when triggered by their description phrases
   - Agents appear in appropriate triggering contexts
   - Hooks activate on events (if applicable)
   - MCP servers connect (if applicable)
   - Settings work (if applicable)

3. Testing recommendations:
   - Skills: Ask questions matching skill descriptions
   - Agents: Create scenarios matching agent examples
   - Hooks: Use `claude --debug` to observe hook execution
   - MCP: Use `/mcp` to verify servers and tools

### Output

Plugin tested and verified

---

## Phase 8: Documentation & Completion

### Goal

Complete documentation and mark plugin ready.

### Actions

1. Verify README completeness:
   - Overview, features, installation, prerequisites, usage
   - For MCP plugins: Document required environment variables
   - For hook plugins: Explain hook activation
   - For settings: Provide configuration templates

2. Create summary:
   - Mark all todos complete
   - List what was created: plugin name, purpose, components, key files
   - Document next steps and iteration recommendations

### Output

Documented plugin ready for use

---

## Key Decision Points (Wait for User)

After each phase: Discovery → Component Planning → Design → Structure → Implementation → Validation → Testing → Documentation. Confirm user input before proceeding.

---

## Throughout All Phases

- Use TodoWrite to track progress at every phase
- Load relevant authoring skill before implementing each component type (skill-authoring, command-authoring, agent-authoring, hook-authoring, mcp-integration, or plugin-settings)
- Follow sinon manifest rules: no version, no agents key, no interface key, proper $schema, author as object
- Apply best practices: strong trigger phrases, imperative form, instructions FOR Claude, `${CLAUDE_PLUGIN_ROOT}` for portability, progressive disclosure, security-first

---

## Begin with Phase 1: Discovery
