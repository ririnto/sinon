# Time Interval Example

## Time Intervals

Time intervals define named schedule windows used by `mute_time_intervals` and `active_time_intervals` on routes.
A top-level `time_intervals:` block holds named entries, and each entry defines one window with `times`, `weekdays`, `days_of_month`, `months`, `years`, and `location` fields.
All specified fields are ANDed together.
Omitted fields are unconstrained.

Business hours in Berlin timezone:

```yaml
time_intervals:
  - name: eu-business-hours
    time_intervals:
      - location: Europe/Berlin
        weekdays:
          - monday:friday
        times:
          - start_time: "09:00"
            end_time: "17:00"
```

For the full field schemas, range syntax, timezone pitfalls, split-window patterns, and version notes, see [`./time-intervals.md`](./time-intervals.md).
