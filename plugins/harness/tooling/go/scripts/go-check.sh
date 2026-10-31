#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

if [ "$#" -ne 1 ]; then
  printf 'Usage: %s <lint|format|nilaway|test>\n' "$0" >&2
  exit 2
fi

# Go lists only the main module outside workspace mode and every use entry in it.
modules="$(go list -m -f '{{.Dir}}')"
if [ -z "$modules" ]; then
  printf 'No Go modules found.\n' >&2
  exit 1
fi
while IFS= read -r module; do
  (
    cd "$module"
    case "$1" in
      lint) golangci-lint run ./... ;;
      format) golangci-lint fmt --diff ./... ;;
      nilaway)
        module_path="$(GOWORK=off go list -m)"
        nilaway -include-pkgs="$module_path" ./...
        ;;
      test) go test -race -shuffle=on -count=1 ./... ;;
      *)
        printf 'Unknown Go check: %s\n' "$1" >&2
        exit 2
        ;;
    esac
  )
done <<EOF
$modules
EOF
