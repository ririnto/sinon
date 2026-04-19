#!/bin/sh
set -eu

payload="$(cat)"

case "$payload" in
  *'".env"'*|*'"credentials.json"'*|*'"secrets"'*)
    printf '%s\n' 'Blocked request that looks like a secret-file edit.' >&2
    exit 2
    ;;
esac

exit 0
