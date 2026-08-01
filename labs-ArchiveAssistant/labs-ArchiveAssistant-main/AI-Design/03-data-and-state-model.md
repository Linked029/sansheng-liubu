# Data And State Model

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. Data concepts come from parser input near line 251, recent topics near line 267, detail tabs near line 293, settings fields near line 318, and manage list near line 376.

Android repo references:

- Use package roots under `app/src/main/java/com/lyihub/archiveassistant/`.
- Keep pure model tests under `app/src/test/java/com/lyihub/archiveassistant/`.
- Use Compose UI state from `MainActivity.kt` or a later `ui` package rather than adding platform storage in the first pass.

## Topic

`Topic` represents a user-managed collection.

Fields:

- `id: String`
- `title: String`, max 20 user-visible characters for create and rename UI.
- `iconName: String`, the symbolic topic icon key used by topic rows.
- `iconColor: String`, the display color token or hex value for the topic icon.
- `updatedAtEpochMillis: Long`, deterministic epoch millis used for sorting and display.

Responsibilities:

- Drives recent topic list and all-topic manage list.
- Provides selected topic title for Detail.
- Derives visible item counts from `KnowledgeItem` lists filtered by `topicId`; counts are not stored on `Topic`.
- Does not own raw API secrets or engine settings.

## KnowledgeItem

`KnowledgeItem` is one saved card inside a topic.

Fields:

- `id: String`
- `topicId: String`
- `contentType: ContentType`
- `title: String`
- `summary: String`
- `fullText: String`
- `sourceUrl: String?`
- `documentFormat: DocumentFormat?`
- `fileName: String?`
- `fileSize: Long?`
- `createdAtEpochMillis: Long`

Responsibilities:

- Drives card feed and card modal.
- Derives visible type labels from `contentType.label`; free-form per-item tags are not part of the model.
- Uses local content only during the first implementation cycle.
- Keeps display preview text in `summary` and full modal or detail text in `fullText`.

## ContentType

`ContentType` maps to the prototype tabs.

Values:

- `ALL`, UI label `全部`.
- `WEB_ARTICLE`, UI label `网页文章`.
- `IMAGE_SCREENSHOT`, UI label `图像截屏`.
- `DOCUMENT_PDF`, UI label `文档/PDF`.
- `PLAIN_TEXT`, UI label `文本片段`.

Filtering rule:

- `ALL` shows every `KnowledgeItem` for the selected topic.
- Other values match `KnowledgeItem.contentType` exactly.

## AiEngineSettings

`AiEngineSettings` stores editable local settings only. The current first-pass model uses `AiEngineType.CLOUD_API` and `AiEngineType.LOCAL_MODEL` as the engine selector, and persists these settings with AndroidX DataStore Preferences.

Fields:

- `engineType: AiEngineType`
- `baseUrl: String`
- `modelName: String`
- `apiKeyAlias: String`
- `localEndpoint: String`

Rules:

- Treat `apiKeyAlias` as a local reference or display alias, not a raw secret.
- Persist `engineType`, `baseUrl`, `modelName`, `apiKeyAlias`, and `localEndpoint` through typed DataStore Preferences keys.
- Keep the raw API key entry UI-local; do not write raw API secrets to DataStore.
- Do not perform real network validation.
- Do not run no real AI API calls in tests or previews.

Knowledge content persistence is deferred in this pass. `Topic` and `KnowledgeItem` values continue to come from in-memory seeded data because real import, storage, and migration requirements are outside the current local settings scope.

## AppPane

`AppPane` describes the active high-level surface.

Values:

- `TOPICS`
- `DETAIL`
- `SETTINGS`
- `CLASSIFICATION_REVIEW`
- `CARD_DETAIL`
- `MANAGE`

`MANAGE` owns the all-topic management surface. Create, rename, and delete confirmation dialogs remain layered state over the current pane rather than separate `AppPane` values.

## Layout Mode State

`LayoutModeState` describes responsive structure.

Fields:

- `windowSizeClass: Compact | Medium | Expanded`
- `foldPosture: Flat | HalfOpen | Tabletop | Unknown`
- `hingeBounds: Rect?`
- `usesTwoPane: Boolean`

Responsibilities:

- Keeps master/detail decisions outside individual feature components.
- Gives UI components hinge-safe content bounds.

## Guardrails

- Must NOT persist real secrets in plain text.
- Must NOT add remote AI response models until network integration is explicitly approved.
- Must NOT let `KnowledgeItem` depend on Android UI classes.
- Must NOT rename the documented fields away from the current Kotlin domain model without updating `Models.kt` and tests together.

## Acceptance Checks

- Unit tests can create sample `Topic`, `KnowledgeItem`, `ContentType`, `AiEngineSettings`, `AppPane`, and `LayoutModeState` values without Android framework dependencies.
- Filtering tests verify the prototype tabs plus the plain-text fallback.
- Settings tests verify API key masking and no real network validation call path.
