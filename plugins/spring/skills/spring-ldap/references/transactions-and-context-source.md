# Spring LDAP transactions and context source tuning

Open this reference when the application needs transaction-aware context sources, connection-pool tuning, or explicit timeout settings beyond the Boot property path.

## Transaction-aware context source

```java
@Bean
ContextSourceTransactionManager contextSourceTransactionManager(ContextSource contextSource) {
    return new ContextSourceTransactionManager(contextSource);
}
```

```java
@Service
class GroupMembershipService {
    private final LdapTemplate ldap;

    @Transactional
    void addMemberToGroups(String userDn, List<String> groupDns) {
        for (String groupDn : groupDns) {
            ldap.modifyAttributes(LdapNameBuilder.newInstance(groupDn).build(),
                new ModificationItem[]{
                    new ModificationItem(DirContext.ADD_ATTRIBUTE,
                        new BasicAttribute("member", userDn))
                });
        }
    }
}
```

Compensating transactions are an advanced branch. Use them only when several LDAP modifications must succeed or roll back together.

These are client-side compensating transactions, not true server-side LDAP transactions. The directory server itself is not aware of the rollback plan.

## Context source tuning shape

```java
@Bean
ContextSource contextSource() {
    LdapContextSource contextSource = new LdapContextSource();
    contextSource.setUrl("ldap://ldap.example.com:389");
    contextSource.setBase("dc=example,dc=com");
    contextSource.setUserDn("cn=admin,dc=example,dc=com");
    contextSource.setPassword(password);
    contextSource.afterPropertiesSet();
    return contextSource;
}
```

## Pooling and timeout shape

```java
@Bean
ContextSource contextSource() {
    LdapContextSource contextSource = new LdapContextSource();
    contextSource.setUrl("ldap://ldap.example.com:389");
    contextSource.setBase("dc=example,dc=com");
    contextSource.setUserDn("cn=admin,dc=example,dc=com");
    contextSource.setPassword(password);
    contextSource.setPooled(true);
    contextSource.setBaseEnvironmentProperties(Map.of(
        "com.sun.jndi.ldap.connect.timeout", "3000",
        "com.sun.jndi.ldap.read.timeout", "5000"
    ));
    contextSource.afterPropertiesSet();
    return contextSource;
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Several LDAP writes must move as one unit | transaction-aware context source |
| Default Boot properties are enough | stay on the common path |
| Context source or pooling needs explicit tuning | custom `LdapContextSource` bean |
