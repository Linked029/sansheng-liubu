# AI Engine Settings Design

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. Settings begin near line 308 with `配置应用设置`, engine controls near line 318, API mode near line 337, and local model fields near line 356.

Android repo references:

- Settings UI should later live under `app/src/main/java/com/lyihub/archiveassistant/` and be shown from the Compose shell in `MainActivity.kt`.
- `app/build.gradle.kts` currently has no network client dependency. Keep it that way for the first settings implementation.
- Tests should use `app/src/test/java/com/lyihub/archiveassistant/` for masking rules and `app/src/androidTest/java/com/lyihub/archiveassistant/` for UI interaction.

## Local-Only Settings

Settings are local UI state in the first implementation cycle. They can be remembered during process lifetime or stored locally in a later task if approved, but they must not be sent to a service.

Cloud mode fields:

- Engine type: `API`.
- Base URL: editable text field, default may be blank or a harmless placeholder.
- API key: secret entry field with masked display.
- Model name: editable text field, for example a non-secret sample model label.

Local mode fields:

- Engine type: `本地模型`.
- Local model: selector with labels from the prototype, such as `Qwen3-2B` and `Gemma 3 4B`.
- Helper text may note that local model performance depends on device hardware.

## API Key Masking

- Mask the key while typing where platform controls allow it.
- After entry, display only a masked value such as `••••••••1234` or a plain ASCII equivalent in tests.
- Never write real keys to logs, previews, fixtures, screenshots, docs, or test output.
- Clearing the key should remove both raw and masked values.

## No Network Validation

- The settings screen must not call the Base URL.
- The save action only updates local state.
- Validation is limited to local field shape, such as empty string handling, if implemented.
- There must be no real AI API calls from settings, tests, previews, or the classify button in this stage.

## Guardrails

- Must NOT add OkHttp, Retrofit, Ktor client, or other network dependencies for this settings task.
- Must NOT make real AI API calls or real key validation calls.
- Must NOT include real secrets or API keys.
- Must NOT imply cloud mode is functional beyond local configuration storage.

## Acceptance Checks

- Unit tests verify API key masking, clearing, and cloud/local mode state transitions.
- Instrumented tests switch between `API` and `本地模型` and confirm the expected fields appear.
- A repo search confirms no network client dependency was added and no real AI API calls exist.
