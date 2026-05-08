# Git Hooks Templates

Install hooks only when `docs/harness-engineering/harness-engineering.json` declares `hooks.enabled: true`.

```bash
mkdir -p .githooks
git config core.hooksPath .githooks
```

## .githooks/commit-msg

```sh
#!/bin/sh
set -eu

# :description: Validate commit message shape for harness-managed repositories.
# :param commit_msg_path: Path to the temporary commit message file.
validate_commit_message() {
    commit_msg_path="$1"
    first_line="$(sed -n '1p' "${commit_msg_path}")"
    case "${first_line}" in
        feat:*|fix:*|docs:*|test:*|refactor:*|chore:*) exit 0 ;;
        *) printf '%s\n' "commit message must use a configured Conventional Commit prefix" >&2; exit 1 ;;
    esac
}

validate_commit_message "$1"
```

## .githooks/pre-push

```sh
#!/bin/sh
set -eu

# :description: Run offline harness validation before pushing.
run_harness_validation() {
    sh scripts/harness/validate_harness.sh
}

run_harness_validation
```

Keep hooks fast and offline. CI remains the authoritative shared enforcement surface.
