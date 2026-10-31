#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

fixture_root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
harness_root=$(CDPATH= cd -- "$fixture_root/../../.." && pwd)
test_root=$(mktemp -d)
trap 'rm -rf "$test_root"' EXIT HUP INT TERM

mkdir -p "$test_root/node" "$test_root/python" "$test_root/rust"
node --import tsx "$harness_root/scripts/configure.ts" --target "$test_root/node" --profile node --eligibility eligible
node --import tsx "$harness_root/scripts/configure.ts" --target "$test_root/python" --profile uv --eligibility eligible
node --import tsx "$harness_root/scripts/configure.ts" --target "$test_root/rust" --profile rust --eligibility eligible
cp -R "$fixture_root/node/src" "$test_root/node/src"
cp "$fixture_root/node/tsconfig.json" "$test_root/node/tsconfig.json"
node -e 'const fs = require("node:fs"); const file = process.argv[1]; const manifest = JSON.parse(fs.readFileSync(file, "utf8")); manifest.exports = "./src/index.ts"; fs.writeFileSync(file, JSON.stringify(manifest, null, 2) + "\n");' "$test_root/node/package.json"
cp -R "$fixture_root/python/src" "$test_root/python/src"
cp "$fixture_root/python/pyproject.toml" "$test_root/python/pyproject.toml"
cp -R "$fixture_root/rust/src" "$test_root/rust/src"
cp "$fixture_root/rust/Cargo.toml" "$test_root/rust/Cargo.toml"

cd "$test_root/node"
pnpm install
pnpm run api:docs
if [ ! -f build/api-docs/functions/findLabel.html ]; then
  printf '%s\n' 'TypeScript API page was not generated.'
  exit 1
fi
rg -q "matching label" build/api-docs/functions/findLabel.html
pnpm exec typedoc src/legacy.js
if [ ! -f build/api-docs/functions/describeKey.html ]; then
  printf '%s\n' 'JavaScript API page was not generated.'
  exit 1
fi
rg -q "JavaScript API" build/api-docs/functions/describeKey.html
printf '\n/** Invalid link to {@link MissingApi}. */\nexport function broken(): void {}\n' >> src/index.ts
if pnpm run api:docs; then
  printf '%s\n' 'TypeDoc unexpectedly accepted a broken link.'
  exit 1
fi
cp "$fixture_root/node/src/index.ts" src/index.ts
pnpm run api:docs

cd "$test_root/python"
pnpm install --ignore-scripts
uv sync --group docs
LC_ALL=C PYTHONCOERCECLOCALE=0 PYTHONUTF8=0 uv run --group docs python docs/conf.py
uv run --group docs sphinx-apidoc -o docs/api src/sample_api
pnpm run uv:doc
if [ ! -f build/api-docs/api/sample_api.html ]; then
  printf '%s\n' 'Python API page was not generated.'
  exit 1
fi
rg -q "matching label" build/api-docs/api/sample_api.html
printf '\n\ndef broken():\n    """Link to :class:`MissingApi`."""\n' >> src/sample_api/__init__.py
if uv run --group docs sphinx-build -n -W -E -b html docs build/api-docs; then
  printf '%s\n' 'Sphinx unexpectedly accepted a broken link.'
  exit 1
fi
cp "$fixture_root/python/src/sample_api/__init__.py" src/sample_api/__init__.py
uv run --group docs sphinx-build -n -W -E -b html docs build/api-docs

cd "$test_root/rust"
pnpm install --ignore-scripts
pnpm run rust:doc
rg -q "label for a key" target/doc/sample_api/fn.find_label.html
RUSTDOCFLAGS="--document-private-items" pnpm run rust:doc
rg -q "private_helper" target/doc/sample_api/index.html
printf '\n/// Broken link to [`MissingApi`].\npub fn broken() {}\n' >> src/lib.rs
if pnpm run rust:doc; then
  printf '%s\n' 'Rustdoc unexpectedly accepted a broken link.'
  exit 1
fi
cp "$fixture_root/rust/src/lib.rs" src/lib.rs
pnpm run rust:doc
