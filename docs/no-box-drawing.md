# Keep your Markdown box-drawing rule

## Repositories using `docs/no-box-drawing`

Copy [rules/no-box-drawing.mjs](../rules/no-box-drawing.mjs) to `rules/no-box-drawing.mjs` in your repository.
Replace the old rule file and update its entry in `customRules` to `"rules/no-box-drawing.mjs"`.
Keep your existing rule settings and inline directives.
The copy uses your existing Markdownlint CLI2 installation and has no runtime imports.
Its diagnostic shows trimmed line context and reports a match immediately after an HTML comment.

## Repositories using `search-replace`

Keep `markdownlint-rule-search-replace` in `customRules` and the `no-box-drawing` entry in `config["search-replace"].rules`.
The plugin diagnostic shows the column and matched character.
The plugin skips a line when its first match starts immediately after an HTML comment.
Keep inline directives under the `search-replace` rule name.

Both forms inspect only the first U+2500 to U+257F character per line.
They report UTF-16 columns and leave the file unchanged under `--fix`.

## Verify your configuration

From the consumer root, run both fixtures through the configured loader.

```sh
node -e 'process.stdout.write("# Tree\n\nASCII +-- | \u24ff \u2580\n")' |
  pnpm exec markdownlint-cli2 --no-globs -
node -e 'process.stdout.write("# Tree\n\n  \u{1f600} \u2500\u257f\n")' |
  pnpm exec markdownlint-cli2 --no-globs -
```

The ASCII and neighboring Unicode fixture exits 0.
The box-drawing fixture exits 1 with one diagnostic from your existing rule ID at line 3, column 6.
Run your repository's normal Markdown check after verifying the fixtures.

In Sinon, run `pnpm run check:test:unit rules/markdownlint-cli2.test.ts` for the complete range, both diagnostic forms, and the HTML comment and inline suppression fixtures.
