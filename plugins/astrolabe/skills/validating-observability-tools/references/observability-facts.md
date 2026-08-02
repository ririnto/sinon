# Observability Facts

Alertmanager uses receiver-specific `send_resolved` defaults.
Email, Slack, Rocket.Chat, and WeChat use a false default.
Use `config/notifiers.go` and the receiver configuration under `notify/` as authority when revalidation is authorized.

`promtool test rules --run` matches test-group names with a regular expression.
The option is repeatable and has no short form.
The `-r` shorthand belongs to TSDB `--human-readable` output.
Use `cmd/promtool/main.go` as authority when revalidation is authorized.
