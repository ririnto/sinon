---
metadata:
  reference:
    Markdownlint CLI2 custom rules:
      url: https://github.com/DavidAnson/markdownlint-cli2#markdownlint-cli2jsonc
    Markdownlint relative-links plugin:
      url: https://github.com/theoludwig/markdownlint-rule-relative-links
---

# Copy Markdownlint rules

## Prose formatting

Copy [prose-format.ts](../rules/prose-format.ts) and its [MIT license](../rules/prose-format.LICENSE) into your repository's `rules/` directory.
Add `"rules/prose-format.ts"` to `customRules` in `.markdownlint-cli2.jsonc`.
If the configuration disables rules by default, set `"docs/prose-format": true` in `config`.
Use Markdownlint CLI2 with a Node.js runtime that can load TypeScript.
The rule has no runtime imports.

`docs/prose-format` checks headings and paragraphs, including list items, for multiple sentences on one source line and for common semicolon joins.
The rule uses English punctuation patterns and allows sentences to span multiple lines.
Some abbreviations followed by a capitalized word can match the sentence pattern.
Code, inline links, and tables are outside its checks.
It reports diagnostics without changing files under `--fix`.

From your repository root, run the configured loader against one sentence per line and then two sentences on one line.

```sh
printf '# Example\n\nStart the service.\nCheck its status.\n' |
  pnpm exec markdownlint-cli2 --no-globs -
printf '# Example\n\nStart the service. Check its status.\n' |
  pnpm exec markdownlint-cli2 --no-globs -
```

The first command exits 0.
The second exits 1 and reports `docs/prose-format` on line 3.
Run your repository's regular Markdown check before replacing an existing rule with the same ID.
In Sinon, run `pnpm run check:test:unit rules/markdownlint-cli2.test.ts` for the copied-source probe.

## Other Markdown checks

Follow the [box-drawing guide](no-box-drawing.md) for the local source and the upstream plugin configuration.
Keep `markdownlint-rule-relative-links` for path and heading resolution.
Keep skill link and frontmatter rules tied to the repository's package layout.
The [Harness pnpm profile](../plugins/harness/docs/tools/pnpm.md) describes its authored-prose and table rules.
Workgraph checks em dashes in headings, tables, and quotations, and checks table delimiters in generated files.
