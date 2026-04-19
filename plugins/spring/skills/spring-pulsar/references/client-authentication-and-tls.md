# Spring Pulsar client authentication and TLS

Open this reference when the cluster requires authentication, TLS, or separate administration credentials.

## Basic secured connection shape

```yaml
spring:
  pulsar:
    client:
      service-url: pulsar+ssl://pulsar.example.internal:6651
      auth-plugin-class-name: org.apache.pulsar.client.impl.auth.AuthenticationToken
      authentication:
        param:
          token: ${PULSAR_TOKEN}
      tls-trust-certs-file-path: /etc/pulsar/ca.crt
      tls-allow-insecure-connection: false
      tls-hostname-verification-enable: true
    admin:
      service-url: https://pulsar.example.internal:8443
```

## Decision points

- Use one credential set for both client and admin access only when the runtime owner also controls topic provisioning.
- Keep token, OAuth, or mTLS material in environment-backed secrets rather than inline YAML.
- Enable hostname verification by default. Disable it only for controlled local development.

## Common pitfalls

- A client `service-url` and an admin `service-url` are separate concerns. Do not assume one implicitly configures the other.
- TLS trust configuration must match the broker certificate chain used by both the client and admin endpoints.
- Authentication plugin names are provider-specific. Keep the property block copy-adapted from the actual deployment rather than generalized.
