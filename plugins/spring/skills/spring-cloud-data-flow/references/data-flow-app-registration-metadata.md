# Spring Cloud Data Flow app registration and metadata

Open this reference when the blocker is versioned coordinates, bulk import, metadata, or curating registered app catalogs.

## Registration blocker

Keep app registration coordinates explicit and versioned.

```text
dataflow:>app register --name http --type source --uri maven://org.springframework.cloud.stream.app:http-source-rabbit:3.2.1
```

## Bulk import blocker

Use bulk import when curating a larger catalog of prepackaged apps.

```properties
source.http=maven://org.springframework.cloud.stream.app:http-source-rabbit:3.2.1
sink.log=maven://org.springframework.cloud.stream.app:log-sink-rabbit:3.2.1
```

```text
dataflow:>app import --uri file:///opt/scdf/apps-rabbitmq.properties
```

Treat imported catalogs as versioned operational inputs, not as hidden defaults.

The current official `rabbitmq-maven-latest` catalog resolves these RabbitMQ stream apps to `3.2.1`, so keep the common-path examples aligned with that curated catalog unless the task explicitly chooses a different app release line.

## Metadata blocker

Use metadata when operators need to inspect app options and deployment properties before wiring topologies.

- Verify the registered app exposes the expected options.
- Keep metadata aligned with the actual app artifact version.

## Verification rule

Verify one `app info` or metadata inspection shows the expected app version and options after registration so the catalog source is not trusted blindly.

## Decision points

| Situation | First check |
| --- | --- |
| One app was registered incorrectly | verify the exact coordinate and app type |
| Many standard apps are needed | use a curated bulk import source |
| Operators cannot see expected options | verify metadata availability for the registered artifact |
