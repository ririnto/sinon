# Spring Security LDAP authentication

Open this reference when the task specifically depends on LDAP-backed authentication and the ordinary servlet path in `SKILL.md` is not enough.

Keep LDAP lookup, password comparison, and authority mapping explicit and well-tested.

## Dependency baseline

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-ldap</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-ldap</artifactId>
</dependency>
```

Add an embedded LDAP server only for development or tests.

## Bind authentication

Use bind authentication as the default path when the LDAP server should verify credentials directly.

```java
@Bean
AuthenticationManager authenticationManager(BaseLdapPathContextSource contextSource) {
    LdapBindAuthenticationManagerFactory factory =
        new LdapBindAuthenticationManagerFactory(contextSource);
    factory.setUserDnPatterns("uid={0},ou=people");
    return factory.createAuthenticationManager();
}
```

### User-search variation

```java
factory.setUserSearchBase("ou=people");
factory.setUserSearchFilter("(uid={0})");
```

## Password-comparison path

Use password comparison only when the LDAP setup requires comparison against a stored password attribute.

```java
LdapPasswordComparisonAuthenticationManagerFactory factory =
    new LdapPasswordComparisonAuthenticationManagerFactory(contextSource, new BCryptPasswordEncoder());
factory.setUserDnPatterns("uid={0},ou=people");
factory.setPasswordAttribute("pwd");
```

## Authority mapping

```java
@Bean
LdapAuthoritiesPopulator authorities(BaseLdapPathContextSource contextSource) {
    DefaultLdapAuthoritiesPopulator populator =
        new DefaultLdapAuthoritiesPopulator(contextSource, "ou=groups");
    populator.setGroupSearchFilter("member={0}");
    return populator;
}
```

## Active Directory shape

```java
@Bean
AuthenticationProvider activeDirectoryAuthenticationProvider() {
    return new ActiveDirectoryLdapAuthenticationProvider(
        "example.com",
        "ldap://company.example.com/"
    );
}
```

## Decision points

| Situation | Use |
| --- | --- |
| LDAP server should validate the password | bind authentication |
| Password attribute comparison is required | password-comparison path |
| Microsoft AD domain integration | Active Directory provider |
