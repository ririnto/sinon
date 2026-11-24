# TLA+ MCP

TLA+ MCP runs SANY, PlusCal, and TLC locally over MCP in Claude Code and Codex.
It installs independently of the upstream `tlaplus@sinon` skills plugin.

## Installation

Make Node.js 18 or later, npm with `npx`, and Java 17 or later available on the host's `PATH`.
Register the Sinon marketplace in your host, then run the matching install command.

```sh
claude plugin install tlaplus-mcp@sinon
codex plugin add tlaplus-mcp@sinon
```

Start a new session to load the server.
The host runs `npx --yes --ignore-scripts tlaplus-mcp@1.0.3` over stdio.
The server package bundles `tla2tools.jar` and needs no API key.
A cold start needs access to the configured npm registry and writes to the npm cache.

## Tools

| Tool | Input and result |
| --- | --- |
| `sany_parse` | Takes TLA+ source text and returns validity and parser diagnostics. |
| `pluscal_translate` | Takes source text containing a PlusCal algorithm and returns the translated source. |
| `tlc_check` | Takes source text and a model configuration object and returns state counts or a violation and trace when available. |

Pass module text in `spec`.
The server has no project path or module search path parameter for supplying a multi-file specification.
TLC creates its model configuration from `config`, such as `{ "init": "Init", "next": "Next", "invariants": ["TypeOK"] }`.
Check `rawOutput` when diagnostics or a parsed trace are incomplete.
Each Java invocation has a two-minute timeout, and the host can time out sooner.

## Files and executable trust

The server writes the supplied text, generated configuration, and TLC working files to temporary directories and attempts to remove them after each call.
TLC can read and overwrite files accessible to Java through modules such as `Json`, including files outside the temporary directory.
The plugin adds no filesystem sandbox.
Run models and configurations you trust, or isolate the Java process from sensitive files.

The launch command runs third-party code with the MCP process's permissions.
`--ignore-scripts` disables npm lifecycle scripts during installation.
npm still executes the server and resolves its transitive dependency ranges.
Use a trusted workspace, `PATH`, npm configuration, and cache because they affect executable and package resolution.
See the [upstream server release](https://github.com/sonoisa/tlaplus-mcp/tree/v1.0.3) for the implementation and license.
