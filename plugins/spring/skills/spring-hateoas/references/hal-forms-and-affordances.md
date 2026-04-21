# Spring HATEOAS HAL-FORMS and affordances

Open this reference when plain HAL links are not enough and the representation must advertise action metadata or HAL-FORMS templates.

## Media-type boundary

Keep plain HAL and HAL-FORMS separate in both server configuration and tests. Affordances can exist as link metadata, but clients see HAL-FORMS `_templates` only when the response is actually rendered as HAL-FORMS rather than ordinary HAL.

## HAL-FORMS boundary

Use HAL-FORMS only when clients will actually consume form templates and action metadata from the representation.

- Good fit: machine-driven UI flows or dynamic clients.
- Poor fit: simple APIs where plain HAL links already express enough.

## Affordance boundary

Use plain affordances when the representation should advertise actionable transitions but the client still consumes ordinary HAL. Move to HAL-FORMS only when the client must also consume form templates and action metadata directly from `_templates`.

## HAL-FORMS response expectation

```text
Content-Type: application/prs.hal-forms+json
```

Use this media type when the API must expose HAL-FORMS templates rather than ordinary HAL links alone.

## HAL-FORMS response shape

```json
{
  "_links": {
    "self": {
      "href": "/orders/1"
    }
  },
  "_templates": {
    "default": {
      "method": "put"
    }
  }
}
```

## Decision points

| Situation | Use |
| --- | --- |
| Link-only navigation is enough | plain HAL |
| Clients need discoverable action metadata | HAL-FORMS |
| Representation should advertise state transitions | affordances |

## Validation rule

Verify that HAL-FORMS responses actually render `application/prs.hal-forms+json` and expose `_templates`, rather than assuming that plain HAL affordances automatically become HAL-FORMS templates.
