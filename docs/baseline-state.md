# Baseline Installation States

The Harness baseline installer checks eligibility before it inspects a target.
For eligible targets it prepares the desired files and previews destination conflicts before creating any files.

```mermaid
stateDiagram-v2
    [*] --> EligibilityChecked
    EligibilityChecked --> Skipped: Target is not eligible
    EligibilityChecked --> Preparing: Target is eligible
    Preparing --> Rejected: Target or destination is unsafe
    Preparing --> Previewing: Desired files are ready
    Previewing --> Rejected: A destination conflicts or is unsafe
    Previewing --> Creating: Every destination is acceptable
    Creating --> Configured: New files are written
    Creating --> WriteFailed: A filesystem write fails
    Skipped --> [*]
    Rejected --> [*]
    Configured --> [*]
    WriteFailed --> [*]
```

Existing identical files are kept, and the Gradle catalog retains its target-owned contents.
The preview rejects conflicting files and unsafe destinations before creation begins.
A later filesystem failure during creation can leave partial output, so the preview is not a transaction against concurrent filesystem changes.

The generated property in `plugins/harness/tests/baseline-state-properties.test.ts` puts a conflicting file at each generated destination for selected profiles.
It checks that rejection preserves the conflict and the target directory entries.
