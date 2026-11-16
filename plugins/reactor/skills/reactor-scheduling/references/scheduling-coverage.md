# Reactor Scheduling Selection

## Coverage map

| Reactor scheduling surface | Common path | Seek more detail when... |
| --- | --- | --- |
| default execution model | source runs on the subscription thread until a scheduler changes it | you are debugging assembly/runtime gaps across many chains |
| scheduler choice | `parallel`, `boundedElastic`, `single`, `immediate` | shared defaults are not enough and you must create, tune, or replace schedulers explicitly |
| `publishOn(...)` vs `subscribeOn(...)` | ordinary placement, effect, and when both are justified | hook-level scheduling instrumentation or shared scheduler replacement becomes the blocker |
| blocking offload | one blocking boundary with `Mono.fromCallable(...)` + `subscribeOn(boundedElastic())` | queue caps, lifecycle, virtual threads, or custom executor bridges matter |
| thread-affinity and context boundary | `Context` flows with the subscription, not the thread | `ThreadLocal` bridging or automatic context propagation becomes the blocker |
| local scheduling diagnostics | `log(...)`, named `checkpoint(...)`, and visible thread logging | global hooks, assembly tracing, or debug-agent level tooling becomes the blocker |
| virtual time boundary | recognize that time control belongs to testing | time simulation becomes the main job |
