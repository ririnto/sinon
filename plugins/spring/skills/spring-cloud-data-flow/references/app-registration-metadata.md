# Spring Cloud Data Flow app registration and metadata

Open this reference when the blocker is versioned coordinates, bulk import, metadata, or curating registered app catalogs.

## Registration blocker

Keep app registration coordinates explicit and versioned.

```text
dataflow:>app register --name http --type source --uri maven://org.springframework.cloud.stream.app:http-source-rabbit:latest
```

## Bulk import blocker

Use bulk import when curating a larger catalog of prepackaged apps.

```text
dataflow:>app import --uri https://dataflow.spring.io/rabbitmq-maven-latest
```

Treat imported catalogs as versioned operational inputs, not as hidden defaults.

## Metadata blocker

Use metadata when operators need to inspect app options and deployment properties before wiring topologies.

- Verify the registered app exposes the expected options.
- Keep metadata aligned with the actual app artifact version.

## Decision points

| Situation | First check |
| --- | --- |
| One app was registered incorrectly | verify the exact coordinate and app type |
| Many standard apps are needed | use a curated bulk import source |
| Operators cannot see expected options | verify metadata availability for the registered artifact |
