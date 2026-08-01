# Verification And QA Plan

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. QA should cover parser flow near line 251, detail tabs near line 293, settings near line 308, manage pane near line 376, and modals near line 389.

Android repo references:

- Root Gradle project: `settings.gradle.kts`, project name `ArchiveAssistant`, module `:app`.
- Android app config: `app/build.gradle.kts`, package `com.lyihub.archiveassistant`, Compose and Material3 enabled.
- Current entry point: `app/src/main/java/com/lyihub/archiveassistant/MainActivity.kt`.
- Local tests: `app/src/test/java/com/lyihub/archiveassistant/ExampleUnitTest.kt`.
- Instrumented tests: `app/src/androidTest/java/com/lyihub/archiveassistant/ExampleInstrumentedTest.kt`.

## Gradle Commands

Run from repo root:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew compileDebugAndroidTestKotlin
./gradlew connectedDebugAndroidTest
```

Expected outcomes for the current repo state:

- `./gradlew testDebugUnitTest`: passes all JVM tests for domain classification, state, layout mode, friendly time, and DataStore persistence.
- `./gradlew assembleDebug`: passes and produces the debug APK.
- `./gradlew compileDebugAndroidTestKotlin`: passes and compiles Compose instrumented tests for Home, Manage, Detail, and Settings panes.
- `./gradlew connectedDebugAndroidTest`: must be attempted when a device or emulator is attached. If the connected device rejects install approval, record the exact blocker, currently `INSTALL_FAILED_ABORTED: User rejected permissions`, and treat it as a device-side environment blocker rather than a code failure.

For foldable QA, prefer a real vivo foldable or a device profile that exposes width changes and posture or hinge data.

## Documentation Checks

```bash
test -d AI-Design
ls AI-Design
rg "Must NOT" AI-Design
rg "no real AI API calls|No Network Validation|Must NOT make real AI API calls" AI-Design
```

Expected file list:

- `00-overall-implementation-plan.md`
- `01-product-functional-analysis.md`
- `02-information-architecture.md`
- `03-data-and-state-model.md`
- `04-ui-module-design.md`
- `05-ai-engine-settings-design.md`
- `06-verification-and-qa-plan.md`

## Functional QA Cases

- Parser: enter text, trigger `智能归纳`, see a local-only classified item appear under a topic.
- Recent topics: create a topic, select it, and confirm Detail opens with that topic title.
- Detail tabs: switch between `全部`, `网页文章`, `图像截屏`, and `文档/PDF`, confirming feed filtering.
- Card modal: open a card and close the modal without losing selected topic or tab.
- Topic manage: open `全部主题`, create or rename a topic, and return to Detail or Home.
- Settings: open settings, switch `API` and `本地模型`, enter an API key, confirm masking, and confirm no real network validation occurs.

## vivo Foldable Layout QA Cases

- Folded compact: Home, Detail, Settings, and Manage are single-pane destinations with working back navigation.
- Unfolded expanded: Home remains visible while Detail, Settings, or Manage occupies the right pane.
- Half-open or tabletop, when exposed: parser input, tabs, settings fields, and modal actions avoid hinge bounds.
- Rotation and posture change: selected topic, parser text, active tab, modal dismissal state, and settings edits remain coherent.
- Official vivo reference: `https://dev.vivo.com.cn/documentCenter/doc/597`. If the page shell blocks content, record that limitation and verify against the applied foldable decisions in `04-ui-module-design.md`.

## Evidence Paths

Store implementation evidence under `.sisyphus/evidence/knowledge-curation-app/`:

- `.sisyphus/evidence/knowledge-curation-app/task-01-docs-existence.md`
- `.sisyphus/evidence/knowledge-curation-app/task-02-domain-classifier-tests.md`
- `.sisyphus/evidence/knowledge-curation-app/task-03-state-entry-tests.md`
- `.sisyphus/evidence/knowledge-curation-app/task-04-responsive-shell.md`
- `.sisyphus/evidence/knowledge-curation-app/task-05-parser-classification.md`
- `.sisyphus/evidence/knowledge-curation-app/task-06-topic-management.md`
- `.sisyphus/evidence/knowledge-curation-app/task-07-detail-feed-modal.md`
- `.sisyphus/evidence/knowledge-curation-app/task-08-settings-pane.md`
- `.sisyphus/evidence/knowledge-curation-app/task-09-datastore-persistence.md`
- `.sisyphus/evidence/knowledge-curation-app/task-10-final-consolidation.md`
- `.sisyphus/evidence/knowledge-curation-app/verification-results.md`

## Guardrails

- Must NOT count docs as implementation proof for runtime behavior.
- Must NOT skip real-device testing before expanding beyond the first usable module.
- Must NOT make real AI API calls during QA for this stage.

## Acceptance Checks

- Docs verification commands pass.
- Later Gradle checks pass or capture pre-existing failures in `.sisyphus/evidence/knowledge-curation-app/`.
- Foldable QA records device, posture, layout mode, and hinge findings.
