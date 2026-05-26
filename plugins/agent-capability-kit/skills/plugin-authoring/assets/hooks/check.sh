#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Secret file blocker hook.
#
# @exit Exits with status 2 if payload contains secret file references.
# @exit Exits with status 0 otherwise.
payload="$(cat)"
case "$payload" in
    *'".env"'* | *'"credentials.json"'* | *'"secrets"'*)
        printf '%s\n' 'Blocked request that looks like a secret-file edit.' >&2
        exit 2
        ;;
esac
exit 0
