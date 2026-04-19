# Spring Data entity callbacks and conversions

Open this reference when the blocker is entity callback registration, custom conversions, or per-property value conversion beyond the ordinary repository path in [SKILL.md](../SKILL.md).

## Entity callback blocker

Use callbacks when persistence lifecycle hooks must stay in Spring Data infrastructure rather than inside the aggregate itself.

```java
@Component
class CustomerBeforeSaveCallback implements BeforeSaveCallback<Customer> {
    @Override
    public Customer onBeforeSave(Customer entity, AggregateChange<?> aggregateChange) {
        return entity;
    }
}
```

Keep callbacks small and explicit because they change shared persistence behavior for every repository write that reaches them.

## Custom conversion blocker

Use custom conversions when a value object needs a stable read or write representation across the store boundary.

```java
@WritingConverter
class EmailWriteConverter implements Converter<EmailAddress, String> {
    @Override
    public String convert(EmailAddress source) {
        return source.value();
    }
}
```

Use `@ReadingConverter` for the reverse direction when the value must round-trip through the store cleanly.

## Property-value conversion blocker

Use per-property conversion only when one field needs conversion semantics that should not apply to the same Java type everywhere else.

## Decision points

| Situation | First check |
| --- | --- |
| Persistence lifecycle needs a shared hook | register an entity callback |
| A value object needs stable store representation | add read or write converters |
| Only one field needs special conversion | prefer property-value conversion |
| Conversion depends on store-specific behavior | move to the store-specific path |
