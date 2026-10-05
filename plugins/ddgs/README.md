---
description: >-
  DDGS web search and content extraction MCP integration for Claude Code and Codex.
metadata:
  reference:
    Agent Plugins:
      version: 1.0.0
      url: https://agent-plugins.org/specification.md
    Claude Code plugin manifests:
      url: https://code.claude.com/docs/en/plugins/manifest-reference.md
    DDGS:
      url:
        - https://raw.githubusercontent.com/deedy5/ddgs/main/README.md
        - https://raw.githubusercontent.com/deedy5/ddgs/main/pyproject.toml
        - https://pypi.org/project/ddgs/
    uv:
      url:
        - https://docs.astral.sh/uv/guides/tools/
        - https://docs.astral.sh/uv/reference/cli/#uv-tool-run
        - https://docs.astral.sh/uv/reference/cli/#uv-cache-clean
    pip-system-certs:
      url: https://pypi.org/project/pip-system-certs/
---

# DDGS

DDGS provides web search and URL content extraction through an MCP server.
The server runs over stdio and exposes text, image, news, video, and book search tools, plus content extraction.

## Runtime

The plugin runs the upstream `ddgs` package through `uvx`.
The `ddgs[mcp]` requirement installs the optional dependencies needed by the MCP server.
The runtime requires `uv`, and the current upstream package supports Python 3.10 or newer.
The `--from` option requests the unversioned package and its `mcp` extra.
The `--with pip-system-certs` option adds system certificate support to the same tool environment.
Python loads that package at startup and uses the operating system certificate store through `truststore`.
`uvx` reuses its ephemeral tool environment in the default uv cache between launches.
Run `uv cache clean` when you want uv to resolve the package again.

## Install And Use

Install the plugin from the Sinon marketplace in Claude Code:

```sh
claude plugin marketplace add ririnto/sinon
claude plugin install ddgs@sinon
```

For local development, load the package in a new session:

```sh
claude --plugin-dir ./plugins/ddgs
```

In Codex, install DDGS from the Sinon repository marketplace and trust the MCP server.
Start a new session after installation.

## MCP Tools

The upstream server exposes these tools:

| Tool | Description |
| --- | --- |
| `search_text` | Search the web for text results. |
| `search_images` | Search for images. |
| `search_news` | Search for news. |
| `search_videos` | Search for videos. |
| `search_books` | Search for books. |
| `extract_content` | Extract content from a URL. |

`search_text` requires `query` and accepts optional region, safe search, time limit, result count, page, and backend settings.
`extract_content` requires `url` and accepts an optional format such as `text_markdown` or `text_plain`.

## Package Contents

- The Sinon Claude marketplace entry declares the plugin metadata.
- `.codex-plugin/plugin.json` declares the Codex plugin.
- `.mcp.json` registers the Claude Code MCP server.
- `mcp.json` registers the portable MCP server configuration.
