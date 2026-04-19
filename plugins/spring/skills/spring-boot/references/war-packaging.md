# Spring Boot war packaging

Open this reference when a traditional servlet container is a hard requirement.

Use war packaging only when the deployment target truly requires a servlet container instead of an executable jar or OCI image.

## Gotchas

- Do not keep war packaging just because it was historically used if the platform now supports executable jars.
