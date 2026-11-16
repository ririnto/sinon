# Panel Type Selection

## Panel Type Decision Guide

Choose the panel type based on what the operator needs to see.
The most common types are listed first.
Full JSON schemas for every panel type, including option keys such as `drawStyle`, `textMode`, and `graphMode`, are in [`./panel-types.md`](./panel-types.md).

| Operator Question | Panel Type | Key Distinguishing Feature |
| --- | --- | --- |
| How does this metric change over time? | `timeseries` | Line/area chart with X = time axis |
| What is the single current value? | `stat` | Large number with optional sparkline |
| What is the value relative to min/max? | `gauge` | Arc gauge with color zones |
| How do categories compare? | `barchart` | Vertical/horizontal bars, grouped/stacked |
| What is the distribution of values? | `histogram` | Bucketed frequency distribution |
| Where is density in two dimensions? | `heatmap` | Color-coded grid (X and Y axes) |
| How is a whole divided? | `piechart` | Proportional slices |
| What are the raw rows? | `table` | Tabular data with sorting/filtering |
| What state was active when? | `statetimeline` | Colored horizontal bands per state |
| How often did states change? | `statushistory` | Timeline of discrete status events |
| What log lines match? | `logs` | Log viewer with highlighting |
| What is the trace detail? | `traces` | Trace waterfall/duration view |
| What is the flame graph? | `flamegraph` | Hierarchical call-stack profiling |
