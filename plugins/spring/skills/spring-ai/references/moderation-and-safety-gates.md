# Moderation and safety gates

Open this reference when the common-path prompt and tool guidance in [SKILL.md](../SKILL.md) is not enough and the blocker is input screening, output screening, or provider-backed moderation policy checks.

## Use this file for one blocker family

Use this file only when the application must explicitly decide whether content is safe before or after model generation.

## Input moderation blocker

**Problem:** the application must reject or route unsafe user content before calling the main model.

**Solution:** call a `ModerationModel` first and branch on the moderation result before entering the normal chat path.

```java
@Service
class InputSafetyService {
    private final ModerationModel moderationModel;

    InputSafetyService(ModerationModel moderationModel) {
        this.moderationModel = moderationModel;
    }

    boolean isAllowed(String text) {
        ModerationResponse response = moderationModel.call(new ModerationPrompt(text));
        return response.getResult().getOutput().getResults().stream().noneMatch(ModerationResult::isFlagged);
    }
}
```

## Output moderation blocker

**Problem:** generated content must be checked before it is shown to the user or sent to another system.

**Solution:** moderate the generated text after the model call and block, redact, or route the result according to the application policy.

```java
String answer = chatClient.prompt().user(question).call().content();
ModerationResponse moderation = moderationModel.call(new ModerationPrompt(answer));
boolean blocked = moderation.getResult().getOutput().getResults().stream().anyMatch(ModerationResult::isFlagged);
```

## Policy threshold blocker

**Problem:** a simple flagged or not-flagged outcome is not enough for the business policy.

**Solution:** inspect moderation categories and scores at the application seam and keep the threshold policy explicit in code or configuration.

```java
Moderation moderation = moderationModel.call(new ModerationPrompt(text)).getResult().getOutput();
ModerationResult first = moderation.getResults().get(0);
boolean sexualBlocked = first.getCategories().isSexual();
```

## Decision points

| Situation | First choice |
| --- | --- |
| Block unsafe prompts before generation | input moderation gate |
| Screen model output before delivery | output moderation gate |
| Domain policy depends on category type or score | inspect categories and scores explicitly |
| Safety requirements differ by route | keep moderation policy at the application seam for each route |

## Pitfalls

- Do not assume provider moderation policies are interchangeable.
- Do not hide moderation policy inside prompt text when the real requirement is an application-level gate.
- Do not treat moderation as a one-time global toggle. Input and output risks are often different.
- Do not log raw flagged content if the application policy treats that content as sensitive.
