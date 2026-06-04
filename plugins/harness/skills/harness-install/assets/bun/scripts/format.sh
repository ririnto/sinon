#!/usr/bin/env sh
# -*- coding: utf-8 -*-
set -e

# Apply ultracite formatting and safe lint fixes.
#
# @return Exits with the ultracite fix status.
main() {
    bunx ultracite fix
}

main "$@"
