# Reactor Core Selection

## Coverage map

| Reactor surface | Common path | Seek more detail when... |
| --- | --- | --- |
| `Flux` / `Mono` choice | cardinality, contract, type-switching operators | the real problem is hot/manual sources rather than ordinary cardinality |
| source creation | `just`, `empty`, `error`, `defer`, `fromCallable`, `fromSupplier`, `fromFuture`, `fromIterable`, `range` | you need `generate`, `create`, `push`, `using`, or other programmatic patterns |
| operator composition | `map`, `flatMap`, `concatMap`, `handle`, `filter`, `take`, `skip`, `collectList`, `reduce`, `switchMap`, `flatMapMany` | ordering, batching, or async fan-out details become the blocker |
| combination | `concat`, `merge`, `zip`, `combineLatest`, `then`, `switchIfEmpty`, `switchOnNext` | exact completion or ordering semantics decide correctness |
| empty/error behavior | `defaultIfEmpty`, `switchIfEmpty`, `onErrorResume`, `onErrorMap`, `doFinally`, `timeout`, `repeat`, bounded retry | you need `Retry` policies, backoff, filtering, retry exhaustion, or repeat exhaustion rules |
| ordinary backpressure | natural demand, `limitRate(...)`, basic overflow choice | `prefetch`, `BaseSubscriber`, or queue growth becomes the blocker |
| threading / schedulers | recognize the boundary only | `publishOn(...)`, `subscribeOn(...)`, scheduler choice, or execution tracing becomes the main problem |
| blocking bridge | one blocking boundary with `Mono.fromCallable(...)` | multiple boundaries, fromRunnable/fromFuture nuances, terminal bridges, or virtual-thread considerations are needed |
| `Context` | `contextWrite(...)`, `deferContextual(...)`, ordinary metadata flow | nested writes, library-facing composition, or precedence rules are the blocker |
| assembly/thread tracing | recognize the boundary only | assembly tracing, global hooks, or thread-hop debugging becomes the main job |
| hot/cold and multicast | recognize the boundary only | repeated work, replay, or shared subscriptions decide the design |
| sink/manual hot-source APIs | recognize the boundary only | manual emission, sink flavor choice, or emit-failure behavior decides the design |
| batching/grouping/windowing | recognize the boundary only | `groupBy`, `window`, or `buffer` shapes the pipeline |
| sequence diagnostics | signal-level `checkpoint(...)`, `log(...)`, or `doOnEach(...)` as narrow tools | signal-level inspection becomes the main job |

## Reactor quick reference

| Need | Default move | Why |
| --- | --- | --- |
| one result | `Mono<T>` | preserves 0..1 semantics directly |
| many results | `Flux<T>` | keeps demand and streaming visible |
| lazy one-shot lookup | `Mono.fromCallable(...)` | captures deferred work once per subscription |
| async step returning a publisher | `flatMap(...)` | flattens the nested publisher |
| async step with ordering | `concatMap(...)` | preserves source order |
| cancellation-aware fan-out (latest-win) | `switchMap(...)` | cancels previous inner on new trigger |
| Mono-to-Flux flatten | `flatMapMany(...)` or `flatMapIterable(...)` | bridges cardinality change |
| synchronous reshape | `map(...)` | keeps the chain simple |
| conditional single-signal emission | `handle(...)` | emit zero or one value per input element |
| static empty fallback | `defaultIfEmpty(...)` | eagerly replaces empty with one value |
| dynamic empty fallback | `switchIfEmpty(...)` | lazily switches to another publisher on empty |
| error fallback | `onErrorResume(...)` | chooses a replacement publisher |
| exception translation | `onErrorMap(...)` | preserves failure flow while changing the type |
| time-bounded operation | `timeout(Duration)` | errors or falls back if no signal arrives in time |
| completion-side repeat | `repeat(n)` or `repeatWhen(...)` | re-subscribes on completion, not error |
| sequential combination | `concat(...)` | later sources wait for earlier completion |
| concurrent combination | `merge(...)` | emits as soon as values arrive |
| pair by index | `zip(...)` | aligns values positionally |
| latest-state recompute | `combineLatest(...)` | recomputes when any source changes |
| lifecycle side effect | `doOnSubscribe` / `doOnNext` / `doOnError` / `doOnCancel` / `doFinally` | observe signals without transforming |
| request metadata | `contextWrite(...)` + `deferContextual(...)` | keeps metadata in the subscription, not the thread |
