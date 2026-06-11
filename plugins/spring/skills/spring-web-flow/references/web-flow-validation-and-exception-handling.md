# Spring Web Flow validation and exception handling

Open this reference when the ordinary wizard flow in `SKILL.md` is not enough and the task needs custom validation timing, grouped validation, or shared recovery paths.

## Validation timing

Validate at the state transition where the user commits a step, not on unrelated transitions.

```xml
<transition on="next" to="confirm" validate="true"/>
```

Keep validation rules close to the model or validator instead of scattering them across actions.

## Exception handling

Use global transitions or exception handlers when one recovery path should apply across many states.

```xml
<global-transitions>
    <transition on-exception="java.lang.Exception" to="technicalError"/>
</global-transitions>
```

Do not hide business-rule failures behind a generic technical error path.

## Gotchas

- Do not validate on backward navigation unless the UX genuinely requires it.
- Do not scatter duplicate error transitions across many states when one shared recovery path is enough.
- Do not use a global technical-error path for expected business validation failures.
