# Invoice workspace

This sample uses pnpm workspaces and a shared dependency catalog.
The invoice package validates line items and imports the money package through a workspace dependency.
The build bundles the invoice CLI, and the tests exercise both packages and the built CLI.

Install Node.js 24.22.3 or a later Node.js 24 release.
Use the [pnpm installation guide](https://pnpm.io/installation#installing-a-specific-version) to install the version declared in `package.json` under `packageManager`.
Run these commands from the sample root.

```sh
pnpm install --frozen-lockfile
pnpm run check
node packages/invoice/dist/invoice.js '[{"unitCents":199,"quantity":3}]'
```

The CLI prints `597`, the total in cents.
The root owns the Harness lint configuration, catalog, lockfile, and Git hooks.
After initializing Git, run `pnpm run prepare` to install the hooks.
