# Client implementation guide

> How agent products integrate skills support. Load this document when targeting an unusual runtime (sandboxed, cloud, custom harness) or when building a skills-compatible client. Most skill authors do not need this file.

This guide walks through the full lifecycle of skills support: discovering skills, telling the model about them, loading their content into context, and keeping that content effective over time.

The core integration is the same regardless of the agent's architecture. Implementation details vary based on two factors:

- Where do skills live? A locally-running agent can scan the user's filesystem for skill directories. A cloud-hosted or sandboxed agent will need an alternative discovery mechanism — an API, a remote registry, or bundled assets.
- How does the model access skill content? If the model has file-reading capabilities, it can read `SKILL.md` files directly. Otherwise, provide a dedicated tool or inject skill content into the prompt programmatically.

## The core principle: progressive disclosure

Every skills-compatible agent follows the same three-tier loading strategy:

| Tier | What is loaded | When | Token cost |
| --- | --- | --- | --- |
| 1. Catalog | Name + description | Session start | ~50-100 tokens per skill |
| 2. Instructions | Full SKILL.md body | When the skill is activated | < 5000 tokens (recommended) |
| 3. Resources | Scripts, references, assets | When the instructions reference them | Varies |

The model sees the catalog from the start, so it knows what skills are available. When it decides a skill is relevant, it loads the full instructions. If those instructions reference supporting files, the model loads them individually as needed.

This keeps the base context small while giving the model access to specialized knowledge on demand. An agent with 20 installed skills does not pay the token cost of 20 full instruction sets upfront — only the ones actually used in a given conversation.

## Step 1: Discover skills

At session startup, find all available skills and load their metadata.

### Where to scan

Most locally-running agents scan at least two scopes:

- Project-level (relative to the working directory): skills specific to a project or repository.
- User-level (relative to the home directory): skills available across all projects for a given user.

Within each scope, scan both a client-specific directory and the `.agents/skills/` convention:

| Scope | Path | Purpose |
| --- | --- | --- |
| Project | `<project>/.<client>/skills/` | Client's native location |
| Project | `<project>/.agents/skills/` | Cross-client interoperability |
| User | `~/.<client>/skills/` | Client's native location |
| User | `~/.agents/skills/` | Cross-client interoperability |

The `.agents/skills/` paths have emerged as a widely-adopted convention for cross-client skill sharing. Scanning `.agents/skills/` means skills installed by other compliant clients are automatically visible.

> [!NOTE]
> Some implementations also scan `.claude/skills/` (both project-level and user-level) for pragmatic compatibility, since many existing skills are installed there. Other additional locations include ancestor directories up to the git root (useful for monorepos), XDG config directories, and user-configured paths.

### What to scan for

Within each skills directory, look for subdirectories containing a file named exactly `SKILL.md`:

```text
~/.agents/skills/
├── pdf-processing/
│   ├── SKILL.md          ← discovered
│   └── scripts/
│       └── extract.py
├── data-analysis/
│   └── SKILL.md          ← discovered
└── README.md             ← ignored (not a skill directory)
```

Practical scanning rules:

- Skip directories that will not contain skills, such as `.git/` and `node_modules/`.
- Optionally respect `.gitignore` to avoid scanning build artifacts.
- Set reasonable bounds (e.g., max depth of 4-6 levels, max 2000 directories) to prevent runaway scanning in large directory trees.

### Handling name collisions

When two skills share the same `name`, apply a deterministic precedence rule. The universal convention: project-level skills override user-level skills.

Within the same scope, either first-found or last-found is acceptable — pick one and be consistent. Log a warning when a collision occurs so the user knows a skill was shadowed.

### Trust considerations

Project-level skills come from the repository being worked on, which may be untrusted (e.g., a freshly cloned open-source project). Consider gating project-level skill loading on a trust check — only load them if the user has marked the project folder as trusted. This prevents untrusted repositories from silently injecting instructions into the agent's context.

### Cloud-hosted and sandboxed agents

If the agent runs in a container or on a remote server, it will not have access to the user's local filesystem. Discovery needs to work differently depending on the skill scope:

- Project-level skills are often the easiest case. If the agent operates on a cloned repository (even inside a sandbox), project-level skills travel with the code.
- User-level and organization-level skills do not exist in the sandbox. Provision them from an external source — for example, cloning a configuration repository, accepting skill URLs or packages through the agent's settings, or letting users upload skill directories through a web UI.
- Built-in skills can be packaged as static assets within the agent's deployment artifact, making them available in every session without external fetching.

## Step 2: Parse SKILL.md files

For each discovered `SKILL.md`, extract the metadata and body content.

### Frontmatter extraction

A `SKILL.md` file has two parts: YAML frontmatter between `---` delimiters, and a markdown body after the closing delimiter. To parse:

1. Find the opening `---` at the start of the file and the closing `---` after it.
2. Parse the YAML block between them. Extract `name` and `description` (required), plus any optional fields.
3. Everything after the closing `---`, trimmed, is the skill's body content.

### Handling malformed YAML

Skill files authored for other clients may contain technically invalid YAML that their parsers happen to accept. The most common issue is unquoted values containing colons:

```yaml
description: Use this skill when: the user asks about PDFs
```

Consider a fallback that wraps such values in quotes or converts them to YAML block scalars before retrying.

### Lenient validation

Warn on issues but still load the skill when possible:

- Name does not match the parent directory name → warn, load anyway.
- Name exceeds 64 characters → warn, load anyway.
- Description is missing or empty → skip the skill (a description is essential for disclosure), log the error.
- YAML is completely unparseable → skip the skill, log the error.

Record diagnostics so they can be surfaced to the user (in a debug command, log file, or UI), but do not block skill loading on cosmetic issues.

### What to store

At minimum, each skill record needs three fields:

| Field | Description |
| --- | --- |
| `name` | From frontmatter |
| `description` | From frontmatter |
| `location` | Absolute path to the SKILL.md file |

Store these in an in-memory map keyed by `name` for fast lookup during activation.

The skill's base directory (the parent directory of `location`) is needed later to resolve relative paths and enumerate bundled resources.

## Step 3: Disclose available skills to the model

Tell the model what skills exist without loading their full content.

### Building the skill catalog

For each discovered skill, include `name`, `description`, and optionally `location`:

```xml
<available_skills>
  <skill>
    <name>pdf-processing</name>
    <description>Extract PDF text, fill forms, merge files. Use when handling PDFs.</description>
    <location>/home/user/.agents/skills/pdf-processing/SKILL.md</location>
  </skill>
  <skill>
    <name>data-analysis</name>
    <description>Analyze datasets, generate charts, and create summary reports.</description>
    <location>/home/user/project/.agents/skills/data-analysis/SKILL.md</location>
  </skill>
</available_skills>
```

Each skill adds roughly 50-100 tokens to the catalog. Even with dozens of skills installed, the catalog remains compact.

### Where to place the catalog

Two approaches are common:

- System prompt section: add the catalog as a labeled section in the system prompt, preceded by brief instructions on how to use skills. Simple and broadly compatible.
- Tool description: embed the catalog in the description of a dedicated skill-activation tool (see Step 4). Cleaner when you have a dedicated activation tool.

### Behavioral instructions

Include a short instruction block alongside the catalog telling the model how and when to use skills.

If the model activates skills by reading files:

```text
The following skills provide specialized instructions for specific tasks. When a task matches a skill's description, use your file-read tool to load the SKILL.md at the listed location before proceeding. When a skill references relative paths, resolve them against the skill's directory (the parent of SKILL.md) and use absolute paths in tool calls.
```

If the model activates skills via a dedicated tool:

```text
The following skills provide specialized instructions for specific tasks. When a task matches a skill's description, call the activate_skill tool with the skill's name to load its full instructions.
```

### Filtering

Hide filtered skills entirely from the catalog rather than listing them and blocking at activation time. This prevents the model from wasting turns attempting to load skills it cannot use.

### When no skills are available

If no skills are discovered, omit the catalog and behavioral instructions entirely. Do not show an empty `<available_skills/>` block or register a skill tool with no valid options.

## Step 4: Activate skills

When the model or user selects a skill, deliver the full instructions into the conversation context.

### Model-driven activation

Two implementation patterns:

- File-read activation: the model calls its standard file-read tool with the `SKILL.md` path from the catalog.
- Dedicated tool activation: register a tool (e.g., `activate_skill`) that takes a skill name and returns the content. Advantages include strip/preserve frontmatter control, structured wrapping for context-management, listing of bundled resources, permission gating, and activation analytics.

> [!TIP]
> If a dedicated activation tool is used, constrain the `name` parameter to the set of valid skill names (e.g., as an enum in the tool schema). This prevents the model from hallucinating nonexistent skill names. If no skills are available, do not register the tool at all.

### User-explicit activation

Users should also be able to activate skills directly. The most common pattern is a slash command or mention syntax (`/skill-name` or `$skill-name`) that the harness intercepts.

### What the model receives

Two options:

- Full file: the model sees the entire `SKILL.md` including YAML frontmatter. Natural outcome with file-read activation.
- Body only (frontmatter stripped): the harness parses and removes the YAML frontmatter, returning only the markdown instructions. Most dedicated-tool implementations take this approach.

### Structured wrapping

If using a dedicated activation tool, consider wrapping skill content in identifying tags:

```xml
<skill_content name="pdf-processing">
# PDF Processing

[rest of SKILL.md body]

Skill directory: /home/user/.agents/skills/pdf-processing
Relative paths in this skill are relative to the skill directory.

<skill_resources>
  <file>scripts/extract.py</file>
  <file>scripts/merge.py</file>
  <file>references/pdf-spec-summary.md</file>
</skill_resources>
</skill_content>
```

Benefits:

- The model can clearly distinguish skill instructions from other conversation content.
- The harness can identify skill content during context compaction.
- Bundled resources are surfaced to the model without being eagerly loaded.

### Listing bundled resources

When a dedicated activation tool returns skill content, it can also enumerate supporting files (scripts, references, assets) in the skill directory — but it should NOT eagerly read them. The model loads specific files on demand when the skill's instructions reference them.

### Permission allowlisting

If the agent has a permission system that gates file access, allowlist skill directories so the model can read bundled resources without triggering user confirmation prompts.

## Step 5: Manage skill context over time

Once skill instructions are in the conversation context, keep them effective for the duration of the session.

### Protect skill content from context compaction

If the agent truncates or summarizes older messages when the context window fills up, exempt skill content from pruning. Skill instructions are durable behavioral guidance — losing them mid-conversation silently degrades the agent's performance without any visible error.

Common approaches:

- Flag skill tool outputs as protected so the pruning algorithm skips them.
- Use the structured tags from Step 4 to identify skill content and preserve it during compaction.

### Deduplicate activations

Track which skills have been activated in the current session. If the model (or user) attempts to load a skill that is already in context, skip the re-injection to avoid the same instructions appearing multiple times.

### Subagent delegation (optional)

Advanced pattern supported by some clients. Instead of injecting skill instructions into the main conversation, run the skill in a separate subagent session. The subagent receives the skill instructions, performs the task, and returns a summary to the main conversation.

This pattern is useful when a skill's workflow is complex enough to benefit from a dedicated, focused session.
