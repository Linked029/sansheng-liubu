# Product Functional Analysis

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. Key labels come from the app shell around line 230, parser around line 251, detail tabs around line 285, settings around line 308, and all-topic management around line 376.

Android repo references:

- `app/src/main/java/com/lyihub/archiveassistant/MainActivity.kt` currently hosts the starter screen and should become the Compose entry for these flows later.
- `app/build.gradle.kts` already includes Compose UI, Material3, preview tooling, and Compose test dependencies.
- `app/src/androidTest/java/com/lyihub/archiveassistant/ExampleInstrumentedTest.kt` verifies package context today and can host future UI tests.

## Product Shape

`聚合拾遗` is a local-first knowledge curation app. The user gives mixed input, asks the app to classify it, then reads collected cards under topics.

## Required Functional Areas

- Parser input: one prominent input area accepts dragged file intent data, links, plain text, and clipboard paste content. Native Android can expose paste and file picker actions rather than browser drag behavior.
- AI classify: `智能归纳` starts classification. During the first usable module this should use fake or deterministic local classification only, with no real AI API calls.
- Recent topics: `最近主题` shows recently used topics, a `新建` action, and an `全部` action to open topic management.
- Detail tabs: selected topic opens a reader with tabs for `全部`, `网页文章`, `图像截屏`, and `文档/PDF`.
- Card modal: tapping a card opens a modal with the enum-derived content type label, title, rendered content, and close or secondary action controls.
- Topic manage: `全部主题` shows all topics and supports create or rename with the 20-character limit from the prototype modal.
- Settings cloud/local toggle: `引擎类型` switches between API and `本地模型`, showing cloud Base URL, API key, cloud model, or local model fields.
- Foldable master/detail adaptation: compact screens use one pane with back navigation. Unfolded screens show master and selected right-side pane together.

## Module Responsibilities

- Home module owns parser input, classify button state, and recent topic list.
- Topic module owns topic create, rename, selection, and manage list data.
- Reader module owns detail tabs, filtered feed, and card modal rendering.
- Settings module owns local-only `AiEngineSettings` editing and masking.
- Layout module owns `AppPane`, compact versus expanded layout choice, and optional fold posture handling.

## Guardrails

- Must NOT claim real network AI integration exists.
- Must NOT send parser content, Base URL, model name, or API key to a remote endpoint in the first implementation cycle.
- Must NOT store real API keys in sample data, screenshots, tests, or docs.

## Acceptance Checks

- Compose UI includes test tags for parser input, classify action, recent topic list, detail tabs, card modal, topic manage pane, and settings pane.
- Unit tests cover local classification result routing by `ContentType`.
- Instrumented tests can open settings, switch cloud/local mode, and confirm no real network validation is triggered.
