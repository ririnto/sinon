---
description: >-
  CodeGraph repository indexing and MCP integration for Claude Code and Codex.
metadata:
  reference:
    Claude Code plugin manifests:
      url: https://code.claude.com/docs/en/plugins/manifest-reference
    Claude Code hooks:
      url: https://code.claude.com/docs/en/hooks
    CodeGraph:
      version: v1.6.1
      url:
        - https://github.com/colbymchenry/codegraph/tree/v1.6.1
        - https://github.com/colbymchenry/codegraph/blob/v1.6.1/src/sync/worktree.ts
    OpenAI plugin packaging:
      url: https://developers.openai.com/plugins/build/plugins
    Agent Plugins:
      version: 1.0.0
      url: https://agent-plugins.org/specification
    Codex hooks:
      url: https://learn.chatgpt.com/docs/hooks
    npm exec:
      url: https://docs.npmjs.com/cli/v11/commands/npx
    Git:
      url: https://git-scm.com/docs/git-rev-parse
---

# CodeGraph

CodeGraph indexes the active repository when a Claude Code session starts or enters a worktree.
Codex startup also initializes and indexes the active repository.
The plugin provides CodeGraph as an MCP server for repository-aware tools.

## Runtime

The plugin runs the official CodeGraph package directly through `npx`.
The runtime requires Node.js, npm, Git, and a POSIX shell on a platform supported by CodeGraph.
On first use, npm prepares CodeGraph automatically in the plugin's writable data directory.
Claude Code supplies `CLAUDE_PLUGIN_DATA`, and Codex supplies `PLUGIN_DATA`.
Without a host data directory, hooks use `XDG_CACHE_HOME` or the user's cache directory under `sinon/codegraph`.
The command uses the unversioned `@colbymchenry/codegraph` package.
Npm may reuse a project-local installation and otherwise resolves the package from the registry.
The `--prefer-online` option checks cached registry metadata for updates.
The MCP server and lifecycle hooks use the same `npx` package command.
The lifecycle hooks run `codegraph init --yes` before `codegraph index` in the host's current working directory.
In Git repositories, the hook adds only `.codegraph` to the Git info exclude file for the primary checkout.
Linked worktrees skip exclude changes and still run initialization and indexing.
Each linked worktree needs its own index to query that branch.
Non-Git directories skip Git exclusion and still run initialization and indexing.

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
npx --yes --prefer-online @colbymchenry/codegraph --version
npx --yes --prefer-online @colbymchenry/codegraph status
```

## Package Contents

- `.claude-plugin/plugin.json` declares the Claude Code plugin.
- `plugin.json` declares the Codex plugin.
- `.mcp.json` registers CodeGraph's MCP server.
- `mcp.json` registers the portable MCP server configuration.
- `hooks/hooks.json` runs repository preparation at Claude Code startup and after `EnterWorktree`.
- `hooks/codex-hooks.json` runs repository preparation at Codex startup.
