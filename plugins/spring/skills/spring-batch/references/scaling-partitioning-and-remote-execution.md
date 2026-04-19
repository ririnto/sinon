# Spring Batch scaling, partitioning, and remote execution

Open this reference when the common-path scaling ladder in [SKILL.md](../SKILL.md) is not enough and the task requires a concrete scaling model such as multithreaded steps, parallel flows, partitioning, local chunking, remote chunking, or remote step execution.

## Multithreaded step blocker

```java
new StepBuilder("importStep", repository)
    .<CustomerInput, Customer>chunk(100, tx)
    .reader(reader)
    .processor(processor)
    .writer(writer)
    .taskExecutor(taskExecutor)
    .throttleLimit(8)
    .build();
```

Use this only when the reader, processor, and writer are thread-safe or partition-isolated.

## Parallel flow blocker

```java
new JobBuilder("parallelJob", repository)
    .start(flowA)
    .split(taskExecutor)
    .add(flowB)
    .end()
    .build();
```

Use parallel flows when whole steps are truly independent and do not contend on shared state.

## Partitioning blocker

```java
partitionStep.partitioner("workerStep", partitioner).step(workerStep).gridSize(8).taskExecutor(taskExecutor)
```

Partitioning fits one logical step that can divide work by range, file, tenant, or key space.

## Distributed execution blocker

Use distributed execution only when one process is not enough.

- local chunking: chunk coordination within the local deployment topology
- remote chunking: manager sends item chunks to workers
- remote step: manager delegates whole step execution to workers

These patterns add operational cost. Prove that simpler scaling fails before choosing them.

## Decision points

| Situation | First check |
| --- | --- |
| One step is too slow | verify thread safety before a multithreaded step |
| Whole branches are independent | use parallel flows |
| One logical step can split by partition key | use partitioning |
| Execution must distribute across workers | choose local chunking, remote chunking, or remote step deliberately |
