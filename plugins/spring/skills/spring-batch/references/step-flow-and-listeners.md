# Spring Batch step flow and listeners

Open this reference when the task involves tasklets, listeners, or flow branching beyond the linear baseline in [SKILL.md](../SKILL.md).

## Tasklet versus chunk blocker

Use chunk processing for item-oriented work. Use tasklets for one-shot actions such as archive, command, cleanup, or trigger steps.

```java
@Bean
Step archiveStep(JobRepository repository, PlatformTransactionManager tx, ArchiveTasklet archiveTasklet) {
    return new StepBuilder("archiveStep", repository).tasklet(archiveTasklet, tx).build();
}
```

If the step loops over many records, it probably belongs in chunk processing instead.

## Flow branching blocker

Keep jobs linear unless runtime branching is genuinely required.

```java
@Bean
Job importJob(JobRepository repository, Step importStep, Step cleanupStep, JobExecutionDecider decider) {
    return new JobBuilder("importJob", repository).start(importStep).next(decider).from(decider).on("CLEANUP").to(cleanupStep).from(decider).on("*").end().build();
}
```

Use decision flows for real runtime branches, not for cosmetic job structure.

## Listener blocker

Attach listeners when you need execution metadata, promoted values, footer handling, or failure classification.

```java
stepBuilder.listener(stepExecutionListener)
```

Prefer listeners for batch lifecycle concerns rather than embedding that logic in processors or writers.

## Decision points

| Situation | First check |
| --- | --- |
| The work is one-shot and not record-oriented | use a tasklet step |
| A job branches at runtime | use a decider or conditional flow only if the branch is real |
| Lifecycle metadata must be captured | attach a listener instead of leaking lifecycle logic into business code |
