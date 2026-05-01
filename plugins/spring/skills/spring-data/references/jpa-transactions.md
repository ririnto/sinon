# Spring Data JPA transaction behavior

Open this reference when the blocker is declared `@Query` transaction behavior, `@Modifying`, or a facade-level transaction boundary around JPA repositories.

For container-level `@Transactional` setup (`@EnableTransactionManagement`, transaction manager wiring), see the spring-framework skill — this reference focuses on JPA repository specifics.

## Repository method inheritance blocker

Repository methods inherited from `SimpleJpaRepository` already carry the standard transaction defaults: reads are `readOnly = true`, writes use a regular transactional boundary.

Declared query methods, including default methods, do not inherit that behavior automatically.

```java
@Transactional(readOnly = true)
@Query("select c from Customer c where c.email = :email")
Optional<Customer> findExplicitlyTransactionalByEmail(@Param("email") String email);
```

## Modifying query blocker

Use `@Modifying` together with `@Transactional` for update or delete queries declared with `@Query`.

```java
@Modifying
@Transactional
@Query("update Customer c set c.active = false where c.lastLogin < :cutoff")
int deactivateDormantCustomers(@Param("cutoff") Instant cutoff);
```

Do not assume `readOnly = true` methods can safely run modifying queries.

## Facade transaction blocker

Use a service-level facade when the transaction must coordinate more than one repository call.

```java
@Service
class CustomerRegistrationService {
    private final CustomerRepository customers;
    private final AuditLogRepository auditLogs;

    CustomerRegistrationService(CustomerRepository customers, AuditLogRepository auditLogs) {
        this.customers = customers;
        this.auditLogs = auditLogs;
    }

    @Transactional
    void register(Customer customer) {
        customers.save(customer);
        auditLogs.save(new AuditLog("customer-registered", customer.id()));
    }
}
```

Keep the transaction boundary at the facade when the use case spans repositories.

## Decision points

| Situation | First check |
| --- | --- |
| declared `@Query` read method | add explicit `@Transactional(readOnly = true)` |
| declared update or delete query | add both `@Modifying` and `@Transactional` |
| use case spans multiple repositories | move the boundary to a service facade |
| transaction behavior seems surprising | verify whether the method is inherited or declared |
