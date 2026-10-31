# uv workspace sample

The application consumes the greeting library as a workspace dependency.
Install Python 3.12 or later and uv, then run:

```sh
pnpm install
uv sync --all-packages
pnpm run uv:check
pnpm run uv:test
pnpm run uv:doc
uv run --package workspace-greeter greeter Ada
uv build --all-packages
```

The root hooks run the Markdown and Ruff checks through project-owned pnpm scripts, and the push hook also runs the application tests.
