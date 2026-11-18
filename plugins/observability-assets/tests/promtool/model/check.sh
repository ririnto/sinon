#!/usr/bin/env sh
# vim: set fileencoding=utf-8 :
set -e

model_name=$1
shift
plugin_dir=$(CDPATH= cd "$(dirname "$0")/../../.." && pwd)
cd "${INIT_CWD:-.}"
model_dir=$(mktemp -d)
trap 'rm -rf "$model_dir"' EXIT

java -XX:+UseParallelGC -cp "$plugin_dir/node_modules/tla-chaos-loop/tools/tla2tools.jar" \
  tlc2.TLC -workers 1 -metadir "$model_dir" \
  -config "$plugin_dir/tests/promtool/model/$model_name.cfg" \
  "$plugin_dir/tests/promtool/model/$model_name.tla" "$@"
