#!/bin/sh
# Secret file blocker hook.
#
# @exit Exits with status 2 if payload contains secret file references.
# @exit Exits with status 0 otherwise.
set -eu
payload="$(cat)"
case "$payload" in
  *'".env"'*|*'"credentials.json"'*|*'"secrets"'*)
    printf '%s\n' 'Blocked request that looks like a secret-file edit.' >&2
    exit 2
    ;;
esac

exit 0
