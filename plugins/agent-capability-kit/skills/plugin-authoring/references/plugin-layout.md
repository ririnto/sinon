# Plugin Layout

Open this file when `SKILL.md` already covers the baseline shape and you need a deeper layout for a plugin that combines surfaces, adds helper directories, or hits a layout exception.

## Composite roots

Use these when the plugin ships multiple optional surfaces and the simple starter tree is no longer descriptive.

```text
your-plugin/
├── .claude-plugin/
│   └── plugin.json
├── commands/
├── agents/
├── skills/
├── hooks/
│   ├── check.sh
│   └── hooks.json
├── .mcp.json
├── servers/
│   └── example-mcp.py
└── output-styles/
    └── executive-summary.md
```

This shape is useful when command, policy, server, and formatting surfaces all ship together.

## Surface-specific helper directories

Some surfaces need adjacent helper files that do not fit the bare root list:

```text
your-plugin/
├── hooks/
│   ├── check.sh
│   └── hooks.json
├── lsp/
│   └── example-lsp.py
├── monitors/
│   ├── monitors.json
│   └── watch.sh
└── servers/
    └── example-mcp.py
```

Use these helper directories only when the associated surface needs local code beside its manifest or content file.

## Layout exceptions

- keep `.claude-plugin/` limited to `plugin.json`
- place bundled executables and support modules at the plugin root, not inside `.claude-plugin/`
- keep runtime output and generated state outside the shipped surface files unless the specific component explicitly expects it
- prefer one helper directory per surface so the plugin tree stays readable when a surface grows beyond one file

## When this file matters

Open this file when you need to compare a combined plugin tree against the manifest, or when a surface needs extra files that would make the baseline tree misleading.
