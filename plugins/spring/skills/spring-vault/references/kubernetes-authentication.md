# Spring Vault Kubernetes authentication

Open this reference when the application already runs inside Kubernetes and Vault login should use the mounted service-account token instead of token or AppRole configuration.

Use Kubernetes auth only when Vault is already configured to trust the cluster and the Vault role is bound to the actual service account.

## Configuration shape

```yaml
spring:
  cloud:
    vault:
      authentication: kubernetes
      kubernetes:
        role: app
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
```

## Decision points

| Situation | Use |
| --- | --- |
| Application runs in-cluster and Vault trusts that cluster | Kubernetes auth |
| Runtime is outside Kubernetes | keep token or AppRole |
| Role binding depends on namespace or service account | align Vault role with the deployed identity |

## Gotchas

- Do not assume Kubernetes auth works just because the pod has a service-account token.
- Do not mismatch the Vault role with the actual namespace and service-account binding.
- Do not mix Kubernetes auth with another auth mode in the same runtime profile.

## Validation rule

Verify the mounted service-account token file, Vault role binding, and pod identity all match before treating Kubernetes auth as a valid runtime path.
