# Representation shapes

## Entity model shape

```json
{
  "id": 1,
  "status": "CREATED",
  "_links": {
    "self": {
      "href": "/orders/1"
    },
    "orders": {
      "href": "/orders"
    }
  }
}
```

## Collection model link shape

```json
{
  "_links": {
    "self": {
      "href": "/orders"
    }
  }
}
```

## Stable relation name shape

```text
self
orders
```

## Initial configuration

### First safe commands

```sh
./mvnw test -Dtest=OrderRepresentationTests
```

```sh
./gradlew test --tests OrderRepresentationTests
```

### HAL-oriented response expectation

```text
Content-Type: application/hal+json
```

### Paged collection response shape

```json
{
  "_embedded": {},
  "_links": {},
  "page": {
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "number": 0
  }
}
```

Default to HAL unless the project already committed to another hypermedia format.
