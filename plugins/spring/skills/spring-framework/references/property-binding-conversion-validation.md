# Spring Framework Data Binding, Conversion, and Validation

Open this reference when the common path in `SKILL.md` is not enough and the task needs advanced data-binding rules, formatter registration, custom converters, or validation groups.

## `DataBinder` with allowed fields

Restrict which fields may be bound when incoming data should not populate the whole object graph:

```java
InventoryForm form = new InventoryForm();
DataBinder binder = new DataBinder(form);
binder.setAllowedFields("maxItems", "warehouse");
binder.bind(new MutablePropertyValues(Map.of("maxItems", "100", "internalOnly", "x")));
```

Use allowed fields when the input source is broader than the fields the application should actually accept.

## Formatter registration

Register a formatter when text input must be parsed and rendered with locale-aware rules:

```java
@Bean
FormattingConversionService conversionService() {
    DefaultFormattingConversionService service = new DefaultFormattingConversionService();
    service.addFormatter(new MoneyFormatter());
    return service;
}
```

Formatter shape:

```java
class MoneyFormatter implements Formatter<Money> {
    @Override
    public Money parse(String text, Locale locale) {
        return Money.parse(text, locale);
    }

    @Override
    public String print(Money value, Locale locale) {
        return value.format(locale);
    }
}
```

Use a formatter when both parsing and rendering matter. Use a converter when only one-way type conversion is needed.

## Custom converters

Add to a `ConversionService`:

```java
@Bean
ConversionService conversionService() {
    DefaultConversionService service = new DefaultConversionService();
    service.addConverter(new MyCustomConverter());
    return service;
}
```

Converter shape:

```java
public class MyCustomConverter implements Converter<String, Money> {
    @Override
    public Money convert(String source) {
        return new Money(source, Currency.getInstance("USD"));
    }
}
```

Register converters before the application starts if the conversion is needed during bean wiring or generic data binding.

## Programmatic binding with a validator

```java
InventoryForm form = new InventoryForm();
DataBinder binder = new DataBinder(form);
binder.addValidators(new InventoryValidator());
binder.bind(new MutablePropertyValues(Map.of("maxItems", "-1")));
binder.validate();
BindingResult result = binder.getBindingResult();
```

Use this shape when the task needs explicit binding and validation outside a controller or form tag flow.

## Generic `ConversionService` injection

Inject the conversion service where needed:

```java
@Autowired
ConversionService conversionService;

void convertValue() {
    Money money = conversionService.convert("100.00", Money.class);
}
```

## Validation groups

Define a group:

```java
interface CreateOrder {
}
```

Apply to a method parameter:

```java
@Transactional
void placeOrder(@Validated(CreateOrder.class) Order order) {
}
```

Group sequence controls order of validation:

```java
@GroupSequence({CreateOrder.class, UpdateOrder.class})
interface FullValidation {
}
```

Use validation groups to apply different validation rules depending on the operation context.

## Method-level validation

Validate method arguments with `@Validated` at the class level after registering method-validation infrastructure:

```java
@Bean
MethodValidationPostProcessor methodValidationPostProcessor() {
    return new MethodValidationPostProcessor();
}
```

Then apply `@Validated`:

```java
@Service
@Validated
class OrderService {
    void place(@NotNull Order order) {
    }
}
```

## Custom validator

```java
public class OrderValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
        return Order.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        Order order = (Order) target;
        if (order.getItems().isEmpty()) {
            errors.rejectValue("items", "empty", "order must have at least one item");
        }
    }
}
```

Register via `LocalValidatorFactoryBean` or a `Validator` bean.

## Decision points

| Situation | Use |
| --- | --- |
| Restrict which input fields may bind | `DataBinder#setAllowedFields(..)` |
| Parse and print locale-aware text values | `Formatter` + `FormatterRegistry` |
| Non-standard property format | custom `Converter` |
| Bind and validate programmatically | `DataBinder` + `Validator` |
| Different rules per operation context | validation groups |
| Validate method arguments at runtime | `@Validated` at class level + method constraint |
