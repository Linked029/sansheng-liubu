# Third-Party Notices

This file records third-party software and bundled resource licensing information for JuHeShiYi / ArchiveAssistant.

The project source code is intended to be distributed under GPL-3.0-or-later. Third-party dependencies and bundled resources remain under their own licenses. This notice is a project-maintained compliance aid, not legal advice.

## Runtime Dependencies

The Android application currently declares these runtime dependencies in `gradle/libs.versions.toml` and `app/build.gradle.kts`:

| Component | Declared coordinate | License | Notes |
| --- | --- | --- | --- |
| Android Gradle Plugin | `com.android.application` | Apache-2.0 | Build plugin. |
| Kotlin Android / Compose plugins | `org.jetbrains.kotlin.*` | Apache-2.0 | Build plugins. |
| AndroidX Core KTX | `androidx.core:core-ktx` | Apache-2.0 | GPLv3-compatible. |
| AndroidX Lifecycle | `androidx.lifecycle:*` | Apache-2.0 | GPLv3-compatible. |
| AndroidX Activity Compose | `androidx.activity:activity-compose` | Apache-2.0 | GPLv3-compatible. |
| AndroidX DataStore Preferences | `androidx.datastore:datastore-preferences` | Apache-2.0 | GPLv3-compatible. |
| Jetpack Compose UI / Material3 | `androidx.compose.*` | Apache-2.0 | GPLv3-compatible. |
| LiteRT LM Android | `com.google.ai.edge.litertlm:litertlm-android` | Apache-2.0 | GPLv3-compatible. Verify release notes before redistributing binaries. |
| OkHttp | `com.squareup.okhttp3:okhttp` | Apache-2.0 | GPLv3-compatible. |
| jsoup | `org.jsoup:jsoup` | MIT | GPL-compatible. |
| PDFBox Android | `com.tom-roush:pdfbox-android` | Apache-2.0 | GPLv3-compatible. |
| desugar_jdk_libs_nio | `com.android.tools:desugar_jdk_libs_nio` | Apache-2.0 | GPLv3-compatible. |

Apache-2.0 dependencies are compatible with GPLv3. They are not generally compatible with GPLv2-only licensing, which is why this project uses GPL-3.0-or-later language.

## Test Dependencies

| Component | Declared coordinate | License | Notes |
| --- | --- | --- | --- |
| JUnit 4 | `junit:junit` | EPL-1.0 | Test-only. |
| AndroidX Test / Espresso | `androidx.test.*` | Apache-2.0 | Test-only. |
| kotlinx-coroutines-test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Apache-2.0 | Test-only. |
| JSON-java | `org.json:json` | JSON License | Test-only. The JSON License includes the "Good, not Evil" restriction and is not treated as GPL-compatible by some projects. It is not linked into the distributed app, but can be replaced with an Apache-2.0 JSON library if strict test-source compliance is required. |

## Bundled Fonts

| File | License status | Notes |
| --- | --- | --- |
| `app/src/main/res/font/ma_shan_zheng_regular.ttf` | SIL Open Font License 1.1 | License text is included at `app/src/main/assets/fonts/ma_shan_zheng_OFL.txt`. The font remains under OFL 1.1. |
| `app/src/main/res/font/san_ji_xing_kai_jian_ti_cu.ttf` | Needs verification | No matching license or provenance file was found in this repository. Verify redistribution rights before release. |
| `app/src/main/res/font/dinglie_song_typeface.ttf` | Needs verification | No matching license or provenance file was found in this repository. Verify redistribution rights before release. |

## Bundled Images And Mock Content

The repository contains many bundled image assets under `app/src/main/res/drawable*` and `app/src/main/res/mipmap*`, plus mock Markdown/PDF content under `app/src/main/res/raw`.

These files are not automatically covered by the project source-code GPL notice unless their copyright owner has granted that license. For a compliant public release, each third-party image, PDF, mock document, icon, texture, and generated/edited asset should be recorded with:

- source or author;
- license or permission basis;
- modification notes, if any;
- attribution text required by the license.

Until that inventory is complete, treat these bundled assets as "provenance pending" and avoid claiming that every non-code asset in the repository is GPL-licensed.

## Maintenance Checklist

Before publishing binary releases or app-store builds:

1. Confirm every packaged font, image, PDF, and mock document has redistribution rights.
2. Replace or remove assets with unclear provenance.
3. Include required attribution and notices in this file or a release notice bundle.
4. Re-check dependency licenses after version upgrades.
