# Agent Frontmatter Patterns

Use these copyable fragments only after the ordinary role and tool boundary are clear.

## Read-Only Discovery

```yaml
color: cyan
tools:
  - Read
  - Glob
  - Grep
```

## Bounded Editor

```yaml
color: green
tools:
  - Read
  - Glob
  - Grep
  - Edit
```

## Isolated Migration

```yaml
color: yellow
tools:
  - Read
  - Glob
  - Grep
  - Edit
  - Bash
maxTurns: 40
isolation: worktree
```

## Preloaded Skill

```yaml
skills:
  - repository-migration
```

Preloading injects the full skill content at startup.
Use it only when every invocation needs that content.

## Main-Session Startup

```yaml
initialPrompt: >-
  Inspect the current repository state and report the highest-priority validation failure.
```

Use `initialPrompt` only when the agent will run as the main session agent through `--agent` or the `agent` setting.
Keep delegated subagent instructions in the Markdown body.

Do not add `hooks`, `mcpServers`, or `permissionMode` to a plugin agent.
Do not add an `Examples` frontmatter field; keep examples in the Markdown body.
