---
title: "Alertmanager Shared Infrastructure Types"
description: "Open this when configuring http_config, tls_config, oauth2, or tracing_config for Alertmanager receivers or global settings."
---

## Alertmanager Shared Infrastructure Types

Use this reference when you need the complete schema for `http_config`, `tls_config`, `oauth2`, and `tracing_config` -- the shared infrastructure types used by receiver configs, global config, and top-level Alertmanager settings.

These types come from the `github.com/prometheus/common/config` Go package (for HTTP/TLS/OAuth2) and Alertmanager's own `tracing` package.

## http_config

Configures the HTTP client used by all receiver types that make outbound HTTP calls. Appears in `global.http_config` and in each receiver type's local `http_config` field.

### Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `authorization` | Authorization | -- | Bearer/basic authentication credentials |
| `oauth2` | OAuth2 | -- | OAuth2 client credentials grant configuration |
| `bearer_token` | Secret | -- | Bearer token for authentication (shorthand) |
| `bearer_token_file` | string | -- | Path to file containing bearer token |
| `tls_config` | TLSConfig | -- | TLS/client certificate configuration |
| `basic_auth` | BasicAuth | -- | Basic authentication (username + password) |
| `proxy_url` | URL | -- | Proxy server URL for outgoing requests |
| `no_proxy` | string | -- | Comma-separated list of hosts to exclude from proxying |
| `proxy_from_environment` | bool | false | Use `HTTP_PROXY`/`HTTPS_PROXY`/`NO_PROXY` environment variables |
| `proxy_connect_header` | map[string]map[string]string | -- | Headers to send to proxy during CONNECT |
| `follow_redirects` | bool | true | Follow HTTP 30x redirects |
| `enable_http2` | bool | true | Enable HTTP/2 support |
| `keep_alives` | bool | true | Enable TCP keep-alives |

### Validation Rules

- At most one of `authorization`, `basic_auth`, `bearer_token`, and `bearer_token_file`. These are mutually exclusive auth mechanisms.
- If both `bearer_token` and `bearer_token_file` are set, validation fails.
- When set at receiver level, completely replaces global `http_config` (not a merge).

### Complete Example

Use the same `http_config` structure under `global:` or inside a specific receiver.

```yaml
http_config:
  tls_config:
    insecure_skip_verify: false
    ca_file: /etc/ssl/certs/ca-bundle.crt
  basic_auth:
    username: alertmanager
    password_file: /etc/alertmanager/http-password
  follow_redirects: true
  enable_http2: true
  proxy_url: http://proxy.example.org:8080
```

## authorization

Configures the `Authorization` header for HTTP requests.

### Fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `type` | string | **yes** | Auth scheme: `"Bearer"`, `"Basic"`, etc. |
| `credentials` | Secret | cond.* | Credential value (inline) |
| `credentials_file` | string | cond.* | Path to file containing credential value |

\* Exactly one of `credentials` or `credentials_file` is required.

### Example

```yaml
authorization:
  type: Bearer
  credentials_file: /etc/alertmanager/bearer-token
```

## basic_auth

Configures HTTP Basic Authentication via the `Authorization: Basic ...` header.

### Fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `username` | string | **yes** | Basic auth username |
| `password` | Secret | cond.* | Basic auth password (inline) |
| `password_file` | string | cond.* | Path to file containing password |

\* Exactly one of `password` or `password_file` is required.

### Example

```yaml
basic_auth:
  username: alertmanager
  password_file: /etc/alertmanager/http-password
```

## oauth2

Configures OAuth2 client credentials grant flow for automatic token refresh.

### Fields

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `client_id` | string | **yes** | OAuth2 client ID |
| `client_secret` | Secret | cond.* | OAuth2 client secret (inline) |
| `client_secret_file` | string | cond.* | Path to file containing client secret |
| `token_url` | string | **yes** | OAuth2 token endpoint URL |
| `scopes` | list of string | no | Scopes to request with the token |
| `endpoint_params` | map[string]string | no | Additional parameters to include in token requests |

\* Exactly one of `client_secret` or `client_secret_file` is required.

### Example

```yaml
oauth2:
  client_id: my-alertmanager-client
  client_secret_file: /etc/alertmanager/oauth2-secret
  token_url: https://auth.example.org/oauth2/token
  scopes:
    - alertmanager:write
    - alertmanager:read
```

## tls_config

Configures TLS for outgoing HTTPS connections. Used by `http_config.tls_config`, email `tls_config`, and `smtp_tls_config`.

### Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `ca_file` | string | -- | Path to CA certificate file for verifying the server |
| `cert_file` | string | -- | Path to client certificate file for mTLS |
| `key_file` | string | -- | Path to client private key file for mTLS |
| `server_name` | string | -- | Override the server name used for TLS verification (SNI) |
| `insecure_skip_verify` | bool | false | Disable server certificate verification (insecure) |
| `min_version` | string | -- | Minimum TLS version (`TLS12`, `TLS13`) |
| `max_version` | string | -- | Maximum TLS version |
| `cipher_suites` | list of string | -- | Allowed cipher suites (depends on Go version) |

### Validation Rules

- If `cert_file` is provided, `key_file` must also be provided (and vice versa).
- `insecure_skip_verify: true` bypasses certificate validation entirely; use only in development or with explicit justification.

### Example

```yaml
tls_config:
  ca_file: /etc/ssl/certs/ca-bundle.crt
  cert_file: /etc/alertmanager/client.crt
  key_file: /etc/alertmanager/client.key
  min_version: TLS12
  server_name: alerts.example.org
```

## tracing_config

Configures distributed tracing for Alertmanager's outbound HTTP calls. Set at the top level as `tracing:`.

### Fields

| Field | Type | Default | Description |
| --- | --- | --- | --- |
| `client_type` | string | -- | Tracing backend: `"GRPC"`, `""` (disabled) |
| `endpoint` | string | -- | Collector endpoint (e.g., OTLP gRPC endpoint) |
| `sampling_fraction` | float64 | -- | Fraction of traces to sample (0.0 to 1.0) |
| `insecure` | bool | false | Skip TLS verification for tracing connection |
| `tls_config` | TLSConfig | -- | TLS config for the tracing connection |

### Example

```yaml
tracing:
  client_type: GRPC
  endpoint: otel-collector:4317
  sampling_fraction: 0.5
  tls_config:
    insecure_skip_verify: true
```
