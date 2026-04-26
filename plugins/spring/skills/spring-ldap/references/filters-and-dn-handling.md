# Spring LDAP filters and DN handling

Open this reference when the task needs complex LDAP filters, escaping, or DN parsing beyond the common path.

## Logical filter shape

```java
ldap.search(query().base("ou=people").where("objectclass").is("person"), mapper);
```

Prefer Spring LDAP query builders first. Drop to raw filter strings only when the filter shape cannot be expressed clearly through the builder API.

## Raw filter fallback

```java
String escaped = LdapEncoder.filterEncode(value);
String filter = "(&(objectClass=person)(cn=" + escaped + "))";
List<Person> people = ldap.search(query().filter(filter), mapper);
```

```java
String filter = "(&(objectClass=person)(cn=" + value + "))";
List<Person> people = ldap.search(query().filter(filter), mapper);
```

```text
Wrong: raw value inserted directly into the filter string.
Right: encode the value first with Spring LDAP helper methods.
```

Use manual escaping only when a raw filter string is unavoidable.

## DN parsing shape

```java
LdapName dn = LdapNameBuilder.newInstance("cn=admins,ou=groups,dc=example,dc=com").build();
String commonName = dn.getRdn(0).getValue().toString();
```

```java
Name id = person.getId();
LdapName dn = (LdapName) id;
String commonName = dn.getRdn(dn.size() - 1).getValue().toString();
```

Use the ODM entity `@Id` value when the application already loaded the entry and needs to inspect DN components without reconstructing the DN string by hand.

## Decision points

| Situation | Use |
| --- | --- |
| Ordinary equality or substring filter | `LdapQueryBuilder` |
| User input contains special characters | explicit filter escaping |
| Logic depends on DN components | `LdapNameBuilder` |

## Validation rule

Verify that every user-provided filter value is escaped before it reaches a raw LDAP filter string and that DN parsing logic matches the actual RDN ordering used by the directory.
