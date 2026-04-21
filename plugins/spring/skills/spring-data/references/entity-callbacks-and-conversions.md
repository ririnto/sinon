# Spring Data entity callbacks and conversions

Open this reference when the blocker is entity callback registration, custom conversions, or per-property value conversion beyond the ordinary repository path in [SKILL.md](../SKILL.md).

## Entity callback blocker

Use callbacks when persistence lifecycle hooks must stay in Spring Data infrastructure rather than inside the aggregate itself.

Concrete callback interfaces and method signatures are store-defined. Use the examples below as imperative callback shapes, and move reactive callback mechanics to the matching store-specific reactive path.

```java
@Component
class CustomerBeforeConvertCallback implements BeforeConvertCallback<Customer> {
    @Override
    public Customer onBeforeConvert(Customer entity) {
        return entity;
    }
}
```

Keep callbacks small and explicit because they change shared persistence behavior for every repository write that reaches them.

Order callback execution deliberately when more than one callback targets the same entity type:

```java
@Component
@Order(10)
class CustomerAfterConvertCallback implements AfterConvertCallback<Customer> {
    @Override
    public Customer onAfterConvert(Customer entity) {
        return entity;
    }
}
```

Lower numeric order values run first. Lambda-style callback beans are unordered and run last, so prefer named callback classes when ordering matters.

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

`@ReadingConverter` and `@WritingConverter` only disambiguate direction. The converter instance still needs registration through the store's `CustomConversions` path before Spring Data can apply it.

If only one property needs conversion semantics, prefer a property-scoped converter over a type-wide converter so the same Java type does not change representation everywhere else.

## Property-value conversion blocker

Use per-property conversion only when one field needs conversion semantics that should not apply to the same Java type everywhere else.

```java
import org.springframework.data.convert.PropertyValueConverter;
import org.springframework.data.convert.ValueConverter;
import org.springframework.data.convert.ValueConversionContext;

class Customer {
    @ValueConverter(EncryptedStringValueConverter.class)
    String ssn;
}

class EncryptedStringValueConverter implements PropertyValueConverter<String, String, ValueConversionContext<?>> {
    @Override
    public String write(String value, ValueConversionContext<?> context) {
        return encrypt(value);
    }

    @Override
    public String read(String value, ValueConversionContext<?> context) {
        return decrypt(value);
    }
}
```

Check store support before choosing this path. Property-value conversion is more specific than a type-wide converter and is not implemented uniformly by every Spring Data module.

Register the converter through the store's `CustomConversions` / `PropertyValueConverterFactory` path before expecting `@ValueConverter` to take effect.

## Decision points

| Situation | First check |
| --- | --- |
| Persistence lifecycle needs a shared hook | register an entity callback |
| A value object needs stable store representation | add read or write converters |
| Only one field needs special conversion | prefer property-value conversion |
| Conversion depends on store-specific behavior | move to the store-specific path |
