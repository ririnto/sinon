# Version alignment

Spring Session 4.1.x targets Spring Boot 4.1.x.
Spring Session 4.0.x targets Spring Boot 4.0.x.
Spring Boot manages the Spring Session version.
Add the session starter and Boot resolves the correct artifact.

Minimum requirements:

- Java 17+
- Spring Framework 6+ when other Spring libraries are used
- Servlet 3.1+ when running in a servlet container
- Redis 2.8+ when using `@EnableRedisHttpSession`

Spring Boot 4.1 applications use the newer Framework 7, Servlet 6.1, and managed Redis client baselines supplied by that Boot line.
Do not present those platform choices as Spring Session's library minimums.
