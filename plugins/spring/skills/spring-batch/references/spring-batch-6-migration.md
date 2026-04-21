# Spring Batch 6 migration notes

Open this reference when the blocker is Spring Batch 6-specific migration behavior rather than the ordinary batch job path in [SKILL.md](../SKILL.md).

## Batch 6 migration blocker

- `StoppableStep` and graceful stop semantics matter more during upgrades that change stop behavior.
- The newer chunk execution model and concurrency behavior should be re-evaluated before copying old fault-tolerance tuning.
- Batch 6 aligns JSON support with newer Jackson baselines, so JSON reader and writer configuration should stay on the same serialization stack as the rest of the application.
- Repository model or infrastructure changes should be reviewed before assuming an older operational metadata strategy still fits.

## Decision points

| Situation | First check |
| --- | --- |
| Shutdown behavior changed after upgrade | verify stop-capable step behavior and restart tests |
| JSON batch components behave differently | verify the Jackson baseline used by the application |
| Concurrency tuning regressed after upgrade | re-check the newer chunk execution model before scaling out |

## Batch 5.x to 6.x infrastructure delta

```java
// Batch 5.x-era infrastructure expectation
@Configuration
@EnableBatchProcessing
class LegacyBatchInfrastructureConfiguration {
}

// Batch 6.x-specific repository path when the deployment intentionally adopts the new repository model
@Configuration
@EnableJdbcJobRepository
class JdbcBatchInfrastructureConfiguration {
}
```

Keep this migration branch separate from the ordinary Boot-managed path. Stable Spring Boot 3.4.x still manages Spring Batch 5.2.x, so Batch 6-specific repository annotations belong only on an intentionally upgraded line.

## Migration verification shape

```java
@Test
void restartAfterUpgradeResumesWithoutDuplicatingCommittedRows() throws Exception {
    JobExecution failed = jobs.launchJob(new JobParametersBuilder().addString("input", "classpath:/customers.csv").toJobParameters());
    JobExecution restarted = jobOperator.restart(failed);
    assertEquals(ExitStatus.COMPLETED.getExitCode(), restarted.getExitStatus().getExitCode());
}
```

## Pitfalls

- Do not bury version-specific notes inside unrelated step or observability references.
- Re-run restart, shutdown, and throughput tests after a major version jump.
- Do not copy Batch 6 repository or operator examples into a Boot-managed 5.2.x application without first upgrading the managed Batch line.
