# Information Architecture

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. The body starts in `app-state-home` near line 230, then uses master, detail, settings, and manage panes around lines 243, 285, 308, and 376.

Android repo references:

- `app/src/main/java/com/lyihub/archiveassistant/MainActivity.kt` is the later navigation host entry.
- `app/build.gradle.kts` has `androidx.activity.compose` and Material3 for native navigation surfaces.
- Existing tests under `app/src/test/java/com/lyihub/archiveassistant/` and `app/src/androidTest/java/com/lyihub/archiveassistant/` should grow with route and UI state checks.

## App States

- Home: parser card, recent topic list, settings trigger, new topic action, all topics action.
- Detail: selected topic title, content type tabs, filtered feed, empty state when no topic is selected.
- Settings: engine type selector, cloud fields, local model fields, save and cancel behavior.
- Manage: all topics list, create topic, rename topic, select topic.
- Card modal: overlays the current pane for item details.
- Topic name modal: overlays home or manage state for create and rename.

## Compact Layout

Compact layout applies to phones and narrow folded widths.

- Home is the root pane.
- Selecting a topic opens Detail as a full-screen destination.
- Settings opens as a full-screen destination from the floating settings trigger.
- Manage opens as a full-screen destination from `全部`.
- Back returns to the previous pane without losing parser text.

## Half-Open Or Tabletop Layout

Use this mode only if posture is exposed by the device APIs or a tested foldable layout library. If posture data is unavailable, fall back to compact or expanded width classes.

- Avoid placing primary text fields, tabs, or modal action rows across the hinge.
- In tabletop posture, keep parser and topic controls on the accessible upper or primary pane, and use the other pane for read-only detail when dimensions allow.
- Keep modal panels inside one physical screen area.

## Unfolded Expanded Layout

Expanded layout applies when there is enough width for a two-pane master/detail structure.

- Master pane shows Home content and remains visible.
- Detail pane shows the selected topic by default.
- Settings and Manage may replace the right pane without hiding Home.
- Card modal is centered within the active right pane or safely within the full window while avoiding hinge bounds.

## State Ownership

- `AppPane` tracks `Home`, `Detail`, `Settings`, `Manage`, and modal overlays.
- `selectedTopicId` controls the detail reader.
- `selectedContentType` controls the tab filter.
- `layoutMode` controls compact, expanded, and posture-aware layout.

## Guardrails

- Must NOT split an interactive control across a hinge or fold boundary.
- Must NOT make expanded layout the only path. Compact phones remain first-class.
- Must NOT make real AI API calls from navigation state changes.

## Acceptance Checks

- Compact UI test selects a topic, sees Detail, presses back, and returns to Home with parser text preserved.
- Expanded UI test opens Settings and Manage in the right pane while Home remains visible.
- Foldable QA confirms no primary control crosses the hinge on a vivo foldable test device or emulator profile.
