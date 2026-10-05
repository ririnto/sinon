---
description: >-
  CodeGraph repository initialization and MCP integration for Claude Code and Codex.
metadata:
  reference:
    Claude Code plugin manifests:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    Claude Code hooks:
      url: https://code.claude.com/docs/en/hooks.md
    Claude Code MCP:
      url: https://code.claude.com/docs/en/mcp.md#exempt-a-server-from-deferral
    CodeGraph:
      version: v1.6.1
      url:
        - https://github.com/colbymchenry/codegraph/tree/v1.6.1
        - https://raw.githubusercontent.com/colbymchenry/codegraph/v1.6.1/src/bin/codegraph.ts
        - https://raw.githubusercontent.com/colbymchenry/codegraph/v1.6.1/src/sync/worktree.ts
        - https://raw.githubusercontent.com/colbymchenry/codegraph/v1.6.1/src/mcp/tools.ts
    OpenAI plugin packaging:
      url: https://developers.openai.com/plugins/build/plugins.md
    Agent Plugins:
      version: 1.0.0
      url: https://agent-plugins.org/specification.md
    Codex hooks:
      url: https://learn.chatgpt.com/docs/hooks.md
    npm exec:
      url: https://docs.npmjs.com/cli/v11/commands/npx
    Node.js system certificates:
      version: 24.15.0
      url: https://nodejs.org/download/release/v24.15.0/docs/api/cli.html#node_use_system_ca1
    Git:
      url: https://git-scm.com/docs/git-rev-parse
---

# CodeGraph

CodeGraph initializes the active repository when a Claude Code session starts or enters a worktree.
Codex startup also initializes the active repository.
The plugin provides CodeGraph as an MCP server for repository-aware tools.

## Runtime

The plugin runs the official CodeGraph package directly through `npx`.
The runtime requires Node.js 24.6 or newer, npm, Git, and a POSIX shell on a platform supported by CodeGraph.
The MCP server and preparation hooks set `NODE_USE_SYSTEM_CA=1`, equivalent to the Node.js `--use-system-ca` option.
This enables system certificates alongside bundled certificates without replacing the caller's `NODE_OPTIONS`.
On first use, npm prepares CodeGraph automatically with its standard cache and user settings.
The command uses the unversioned `@colbymchenry/codegraph` package.
Npm may reuse a project-local installation and otherwise resolves the package from the registry.
The MCP server and lifecycle hooks use the same `npx` package command.
Claude Code loads CodeGraph tools upfront through `alwaysLoad: true`.
That Claude-specific field is outside the portable MCP schema, so `.mcp.json` does not declare that schema.
The preparation handler resolves the Git checkout root and runs `codegraph init --yes` there.
Initialization builds the first index, and the MCP server then synchronizes changes automatically.
The lifecycle hooks do not run a separate full index command.
CodeGraph 1.6.1 supports `init --yes` to skip prompts and select upstream defaults.
When file watching is unavailable, that default can install Git sync hooks, including in an already initialized repository.
A separate startup handler configures Git exclusion.
Both hosts declare a one-hour timeout and a preparation status message.
Claude Code does not enforce the timeout for asynchronous command hooks.
At startup, the exclusion handler uses Git to find the info exclude file and adds only `.codegraph` when missing.
Linked worktrees use their shared Git exclude file.
The `EnterWorktree` hook runs preparation only.
Each linked worktree needs its own index to query that branch.
For an existing MCP connection, pass the worktree root as `projectPath` in tool calls.
Git exclusion errors do not block the independent asynchronous preparation handler.
Git lookup failures are logged, and the simplified preparation command can continue in the current directory.

## Install And Use

Install the plugin from the Sinon marketplace in Claude Code:

```sh
claude plugin marketplace add ririnto/sinon
claude plugin install codegraph@sinon
```

For local development, load the package in a new session:

```sh
claude --plugin-dir ./plugins/codegraph
```

In Codex, install CodeGraph from the Sinon repository marketplace and trust its startup hook.
Start a new session after installation.
Codex uses startup preparation, while Claude Code also prepares a repository after `EnterWorktree`.

Run CodeGraph directly in a project:

```sh
npx --yes @colbymchenry/codegraph --version
npx --yes @colbymchenry/codegraph status
```

## Package Contents

- The Sinon Claude marketplace entry declares the plugin metadata.
- `.codex-plugin/plugin.json` declares the Codex plugin and its startup hooks.
- `.mcp.json` registers CodeGraph's MCP server.
- `mcp.json` registers the portable MCP server configuration.
- `hooks/hooks.json` runs repository preparation at Claude Code startup and after `EnterWorktree`.
- `hooks/codex-hooks.json` runs repository preparation at Codex startup.
