# Node Package Script Integration

Add this script block to the target repository root `package.json` when `node.enabled` is true. Preserve existing scripts and package-manager choices.

```json
{
  "scripts": {
    "harness:validate": "sh scripts/harness/validate_harness.sh",
    "harness:check": "sh scripts/harness/validate_harness.sh"
  }
}
```

Use the package manager already declared by the target repository.

| Signal | Manager | Install | Run |
| --- | --- | --- | --- |
| `packageManager` starts with `pnpm@` or `pnpm-lock.yaml` exists | pnpm | `pnpm install --frozen-lockfile` | `pnpm run harness:validate` |
| `packageManager` starts with `yarn@` or `yarn.lock` exists | Yarn | `yarn install --immutable` | `yarn run harness:validate` |
| `packageManager` starts with `bun@`, `bun.lock`, or `bun.lockb` exists | Bun | `bun install --frozen-lockfile` | `bun run harness:validate` |
| `package-lock.json` exists or no stronger signal exists | npm | `npm ci` | `npm run harness:validate` |

For workspaces, keep the root `harness:validate` script as the stable CI entrypoint and delegate internally to the target's preferred tool, such as `pnpm -r run harness:validate`, `yarn workspaces foreach run harness:validate`, `npm run harness:validate --workspaces`, or a Turbo/Nx task.
