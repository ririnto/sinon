# Transport artifacts

| Need | Artifact |
| --- | --- |
| Ordinary GraphQL controllers, schema resources, and HTTP endpoint | `spring-boot-starter-graphql` |
| GraphQL-focused test slices and tester support | `spring-graphql-test` |
| HTTP transport | `spring-boot-starter-webmvc` or `spring-boot-starter-webflux` |
| WebSocket transport (Servlet-based apps) | `spring-boot-starter-graphql` + `spring-boot-starter-websocket` |
| WebSocket transport (WebFlux-based apps) | `spring-boot-starter-webflux` (no extra starter) |
| SSE transport for subscriptions | none (uses the HTTP handler) |
| RSocket transport | `spring-boot-starter-rsocket` + Spring WebFlux on Reactor Netty |
