# Audio transcription and speech output

Open this reference when the common-path text workflow in [SKILL.md](../SKILL.md) is not enough and the blocker is speech-to-text, text-to-speech, or audio-bearing model input.

## Use this file for one blocker family

Use this file only when the application must either transcribe audio into text or synthesize text into audio.

## Audio transcription blocker

**Problem:** the application receives audio and must convert it into text before any ordinary prompt or workflow can continue.

**Solution:** keep transcription behind a `TranscriptionModel` seam and treat it as a separate step from the chat call.

```java
@Service
class TranscriptionService {
    private final TranscriptionModel transcriptionModel;

    TranscriptionService(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }

    String transcribe(Resource audioFile) {
        return transcriptionModel.transcribe(audioFile);
    }
}
```

When runtime options matter, pass them explicitly through an `AudioTranscriptionPrompt` instead of hiding them in application logic.

## Speech output blocker

**Problem:** the feature must return spoken audio instead of only text.

**Solution:** keep text-to-speech behind a `TextToSpeechModel` seam and return audio bytes or a streamed response from the application edge.

```java
@Service
class NarrationService {
    private final TextToSpeechModel textToSpeechModel;

    NarrationService(TextToSpeechModel textToSpeechModel) {
        this.textToSpeechModel = textToSpeechModel;
    }

    byte[] narrate(String text) {
        return textToSpeechModel.call(text);
    }
}
```

## Audio-bearing chat input blocker

**Problem:** the model must reason over an audio artifact inside a multimodal chat flow.

**Solution:** keep the audio payload on the user message and keep the model choice explicit.

```java
ChatResponse response = chatClient.prompt()
    .user(user -> user
        .text("Transcribe this call and extract action items.")
        .media(new MimeType("audio", "mp3"), new ClassPathResource("/call.mp3")))
    .call()
    .chatResponse();
```

## Decision points

| Situation | First choice |
| --- | --- |
| Need text from speech | `TranscriptionModel` |
| Need speech from text | `TextToSpeechModel` |
| Need a chat model to reason over audio plus text | multimodal chat model with explicit audio media |
| Need all three | split the pipeline into transcription, reasoning, and optional speech synthesis instead of collapsing them into one opaque call |

## Pitfalls

- Do not hide transcription inside a chat workflow when the actual contract is speech-to-text.
- Do not assume the same provider or model family covers transcription, speech synthesis, and chat equally well.
- Do not return generated audio without an explicit media type or transport decision at the application edge.
- Do not forget to track usage and latency separately for transcription and speech steps when both appear in one request path.
