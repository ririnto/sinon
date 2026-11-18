#!/usr/bin/env sh
# vim: set fileencoding=utf-8 :
set -e

model_name=$1
shift
model_dir=$(mktemp -d)
trap 'rm -rf "$model_dir"' EXIT

java -XX:+UseParallelGC -cp node_modules/tla-chaos-loop/tools/tla2tools.jar \
  tlc2.TLC -workers 1 -metadir "$model_dir" \
  -config "plugins/observability-assets/tests/promtool/model/$model_name.cfg" \
  "plugins/observability-assets/tests/promtool/model/$model_name.tla" "$@"
