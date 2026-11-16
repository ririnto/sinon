# Dependencies and configuration

## Dependency baseline

Use direct Spring LDAP artifacts for the ordinary 4.1.x path.
The `4.1.1` versions below illustrate the documented direct-dependency pairing.
For new direct dependencies, check `org.springframework.ldap:spring-ldap-core` and `org.springframework.data:spring-data-ldap` on Maven Central for stable releases compatible with the project's Boot and Framework lines.
Keep existing BOM or version-catalog pins unless the task authorizes changing them.
Treat Spring Boot starter wiring as a parallel Boot-managed path only when the project is already on the matching Boot generation.

### Runtime baseline

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ldap</groupId>
        <artifactId>spring-ldap-core</artifactId>
        <version>4.1.1</version>
    </dependency>
</dependencies>
```

### Repository-backed direct path

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ldap</groupId>
        <artifactId>spring-ldap-core</artifactId>
        <version>4.1.1</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-ldap</artifactId>
        <version>4.1.1</version>
    </dependency>
</dependencies>
```

Use this direct path when repository-backed queries are required outside Boot-managed dependency alignment.

### Boot-managed alternative

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-ldap</artifactId>
    </dependency>
</dependencies>
```

Use the Boot starter only when the project is already on a Boot line that manages the intended Spring LDAP generation.

### Test-only additions

Add the Spring LDAP test module when tests need LDAP-specific assertions or helpers.
If the test path uses `spring.ldap.embedded.*`, also add an embedded LDAP server implementation for tests.

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.ldap</groupId>
        <artifactId>spring-ldap-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>com.unboundid</groupId>
        <artifactId>unboundid-ldapsdk</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Feature-to-artifact map

| Need | Artifact |
| --- | --- |
| Ordinary LDAP access with `LdapTemplate` or `LdapClient` | `spring-ldap-core` |
| Repository-backed LDAP queries with `LdapRepository` | `spring-data-ldap` plus `spring-ldap-core` |
| Boot-managed LDAP integration | `spring-boot-starter-data-ldap` |
| LDAP-specific test helpers | `spring-ldap-test` |
| Embedded test server implementation | `unboundid-ldapsdk` or another proven embedded LDAP server |

## First safe configuration

### First safe commands

```sh
./mvnw test -Dtest=PersonRepositoryTest
```

```sh
./gradlew test --tests PersonRepositoryTest
```

### Embedded LDAP test configuration

Boot-managed property shape:

```yaml
spring:
  ldap:
    embedded:
      base-dn: dc=example,dc=com
      validation:
        enabled: false
  data:
    ldap:
      repositories:
        enabled: true
```

Disable validation only when the embedded server does not publish a schema compatible with the test data.

### Production context source properties

Boot-managed property shape:

```yaml
spring:
  ldap:
    urls: ldap://ldap.example.com:389
    base: dc=example,dc=com
    username: cn=admin,dc=example,dc=com
    password: ${LDAP_ADMIN_PASSWORD}
```

Always externalize credentials.
Use environment variables or secrets management, never hard-code passwords.

### Direct Spring LDAP context source shape

```java
@Configuration
class LdapConfiguration {
    @Bean
    BaseLdapPathContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://ldap.example.com:389");
        contextSource.setBase("dc=example,dc=com");
        contextSource.setUserDn("cn=admin,dc=example,dc=com");
        contextSource.setPassword(password);
        contextSource.afterPropertiesSet();
        return contextSource;
    }

    @Bean
    LdapTemplate ldapTemplate(BaseLdapPathContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }

    @Bean
    LdapClient ldapClient(BaseLdapPathContextSource contextSource) {
        return LdapClient.create(contextSource);
    }
}
```

Use this direct bean path as the ordinary 4.1.x baseline when the project is not relying on Boot-managed LDAP configuration.

### Direct repository configuration shape

```java
@Configuration
@EnableLdapRepositories(basePackages = "com.example.ldap")
class RepositoryConfiguration {
    @Bean
    BaseLdapPathContextSource contextSource() {
        LdapContextSource contextSource = new LdapContextSource();
        contextSource.setUrl("ldap://ldap.example.com:389");
        contextSource.setBase("dc=example,dc=com");
        contextSource.setUserDn("cn=admin,dc=example,dc=com");
        contextSource.setPassword(password);
        contextSource.afterPropertiesSet();
        return contextSource;
    }
}
```

Use this configuration only when the direct non-Boot path needs `LdapRepository`.
If the task only needs `LdapTemplate`, keep the simpler direct bean path above.
