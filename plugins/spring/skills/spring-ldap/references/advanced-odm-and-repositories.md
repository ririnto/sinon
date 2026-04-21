# Spring LDAP advanced ODM and repositories

Open this reference when the schema needs multi-valued ODM fields, raw `@Query`, or repository behavior beyond the simple derived-query path in `SKILL.md`.

## Advanced ODM mapping

```java
@Entry(objectClasses = {"inetOrgPerson"})
class Person {
    @Id
    private Name id;

    @DnAttribute("cn")
    private String commonName;

    @Attribute("memberOf")
    private Set<String> groups;

    @Attribute("manager")
    private String managerDn;
}
```

Multi-valued attributes such as `memberOf` map to collections. DN attributes belong on `@DnAttribute`, not on `@Attribute`.

## Repository `@Query` shape

```java
interface PersonRepository extends LdapRepository<Person> {
    @Query("(&(objectClass=person)(cn={0}))")
    List<Person> findByExactCommonName(String commonName);
}
```

Use `@Query` only when method-name derivation cannot express the required filter shape.

## Repository derivation boundary

- Stay on the common path when one property name maps cleanly to one LDAP attribute.
- Open this reference when repository naming becomes unclear, when substring or compound naming is no longer readable, or when raw `@Query` is the safer expression.

For complex filter escaping or DN extraction around those repository queries, also open [filters-and-dn-handling.md](filters-and-dn-handling.md).

## Decision points

| Situation | Use |
| --- | --- |
| Multi-valued attributes map cleanly to fields | ODM annotations |
| Repository method name expresses the filter | derived query method |
| Repository method name cannot express the filter | raw `@Query` |

## Validation rule

Verify that multi-valued attributes map to the intended Java collection type and that any raw `@Query` still escapes user-provided values before building the final LDAP filter.
