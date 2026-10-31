# Storybook Smoke Fixtures

These stories exercise the materialized HTML and React Storybook renderers.
Run them from the repository root with its declared Node and pnpm versions:

```sh
set -e
for renderer in html react; do
  target=$(mktemp -d)
  profile=$renderer
  if [ "$renderer" = html ]; then
    profile=frontend
  fi
  node plugins/harness/scripts/configure.ts --target "$target" --profile "$profile" --eligibility eligible --non-interactive
  mkdir -p "$target/src"
  cp "plugins/harness/tests/fixtures/storybook/$renderer/button.stories.js" "$target/src/"
  pnpm --dir "$target" install
  pnpm --dir "$target" run check:storybook
  rm -r "$target"
done
```

The build verifies the renderer and story compilation.
It does not verify browser interaction or appearance.
The target is retained for inspection when a command fails.
