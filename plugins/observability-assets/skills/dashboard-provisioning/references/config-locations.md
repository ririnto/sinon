# Grafana Configuration Locations

## Grafana Config File Locations

Grafana discovers provisioning config from these paths at startup:

| Environment | Provider YAML location | Default dashboard base |
| --- | --- | --- |
| Docker / official image | `/etc/grafana/provisioning/dashboards/*.yaml` | `/var/lib/grafana/dashboards` |
| Linux package install | `<grafana_home>/conf/provisioning/dashboards/*.yaml` | `<grafana_home>/data/dashboards` |
| Custom binary | set via `[paths].provisioning` in `grafana.ini` | set via `[paths].data` |

Config file load order (later values override earlier):

```text
1. $GF_PATHS_HOME/conf/defaults.ini          (bundled defaults)
2. $GF_PATHS_HOME/conf/custom.ini             (distribution overrides)
3. /etc/grafana/grafana.ini                   (system-level config)
4. --config=<path> CLI flag                   (explicit override)
5. GF_ environment variables                  (highest priority)

```

Use when: you need to know exactly where Grafana reads its config and which file takes precedence for a given setting.
