# Overall Implementation Plan

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`, especially the body shell near line 230, parser area near line 251, detail pane near line 285, settings pane near line 308, and manage pane near line 376.

Android target repo paths:

- `settings.gradle.kts`, root project `ArchiveAssistant`, app module `:app`.
- `app/build.gradle.kts`, namespace and application id `com.lyihub.archiveassistant`, Compose and Material3 already enabled.
- `app/src/main/java/com/lyihub/archiveassistant/MainActivity.kt`, current starter `Greeting` UI to replace in a later implementation task.
- `app/src/test/java/com/lyihub/archiveassistant/ExampleUnitTest.kt` and `app/src/androidTest/java/com/lyihub/archiveassistant/ExampleInstrumentedTest.kt`, current test entry points.

## Native Compose Scope

Build a native Android Compose version of `聚合拾遗`. The app should reproduce the prototype's functional structure without embedding the HTML prototype or using a WebView. The first implementation should pause after the first usable module so real-device testing can happen before broadening the surface.

## Feature Modules

- Home parser: accepts text, pasted content, links, file references, and image or document descriptions as local input objects. The prototype label is `拖拽文件、输入链接、纯文本，或直接从剪切板粘贴...`.
- AI classify flow: provides the `智能归纳` action and assigns content to topics by local fake data or deterministic rules until a real engine is approved.
- Recent topics: shows the `最近主题` list with create and all-topic entry points.
- Detail reader: shows selected topic title, tabs for `全部`, `网页文章`, `图像截屏`, and `文档/PDF`, plus card feed and detail modal.
- Topic manage: shows `全部主题`, create, rename, and basic topic actions.
- Settings: shows `配置应用设置`, cloud or local engine mode, Base URL, API key, cloud model, and local model fields.
- Foldable adaptation: uses a compact single-pane flow, expanded master/detail flow, and tabletop or half-open behavior when posture data is available.

## Execution Order

1. Establish domain model and seed data for `Topic`, `KnowledgeItem`, `ContentType`, `AiEngineSettings`, and layout state.
2. Replace the starter `Greeting` screen in `MainActivity.kt` with the home parser and topic list inside `ArchiveAssistantTheme`.
3. Add detail pane and tabbed item filtering.
4. Add card modal and topic create or rename modal.
5. Add settings pane with local-only persistence and masked key display.
6. Add manage pane and expanded layout behavior.
7. Add foldable posture handling, hinge avoidance, and device QA coverage.

## Guardrails

- Must NOT edit app code during this documentation task.
- Must NOT use a WebView or ship the HTML prototype as the runtime UI.
- Must NOT make real AI API calls in the first implementation cycle.
- Must NOT validate API keys against a remote network service.
- Must NOT store or display real secrets in clear text after entry.
- Persist AI engine settings locally with DataStore Preferences; defer topic and knowledge item persistence until real import and storage requirements are defined.
- Must NOT invent features outside the prototype and plan.

## Acceptance Checks

- `test -d AI-Design` passes.
- `ls AI-Design` shows exactly the seven markdown files listed in this documentation set.
- A search for `Must NOT` and `no real AI API calls` finds guardrail text in the docs.
- Later implementation must keep Compose and Material3 dependencies in `app/build.gradle.kts` and use `com.lyihub.archiveassistant` package paths.
