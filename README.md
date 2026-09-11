# Modern T9

A T9 keyboard for current Android that never touches the network — with a prediction
engine you can swap out.

<!-- Screenshot: replace with a real capture of the main keyboard pane, ~1080 px wide.
     Suggested path: docs/screenshots/main.png  (also used by fastlane/ for F-Droid) -->
<p align="center">
  <img src="docs/screenshots/main.png" alt="Modern T9 main keyboard pane" width="360">
</p>

---

## Why it exists

Keyboards are the most privileged app on your phone: they see every password, every
message, every search — and the popular ones phone home.

Modern T9 is the opposite, by construction:

- **No `INTERNET` permission.** There is no network code. The operating system would
  refuse a connection even if one were attempted.
- **No analytics, ads, telemetry, or crash reporting.** Nothing is collected.
- **Everything stays on the device.** Learned words and phrases live in app-private
  storage as plain text files you can inspect. Clipboard history is held in memory only
  and never written to disk.
- **It's T9.** Nine keys, real predictive text, and the speed that comes from a layout
  your thumb learns in an afternoon.

## Install

<!-- Once accepted on F-Droid, replace the placeholder link with the real listing -->
<a href="https://f-droid.org/packages/io.github.jcastell7.modernt9/">
  <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="70">
</a>

Or download the signed APK from the
[latest release](https://github.com/jcastell7/modern-t9/releases/latest) and install it
directly. Each release lists the APK's SHA-256 so you can verify what you downloaded.

Then enable it — Android requires an explicit opt-in for any keyboard:

1. Open **Modern T9** from the launcher.
2. Tap **Enable keyboard in system settings** and switch Modern T9 on. Android shows a
   warning here for every third-party keyboard; it's standard.
3. Tap **Switch to Modern T9**, or use the keyboard-switch icon in any text field.

Requires Android 8.0 (API 26) or newer.

## Features

**Typing**
- T9 predictive input on a 3×3 keypad, with `@` on the `1` key so email addresses are
  typeable
- Candidate strip with the chosen word highlighted — space accepts it
- Left-hand column to disambiguate a key: tap the letter you meant and the candidates
  re-filter around it
- Long-press any key for its accented forms; swipe down to type its digit
- **ABC mode** — turn prediction off and type letter-by-letter, classic multi-tap style
- Re-edit any word by placing the caret in it: alternatives reappear, and edits land at
  the caret rather than the end of the word
- Contractions found without the apostrophe — typing `dont` offers `don't`

**Learning**
- Learns the words you use, including single-letter words like *I* and *y*
- Next-word prediction from your own writing
- **Phrases**: save email addresses, URLs or handles and reach them from the first few
  letters — `user` brings up `user@example.com`
- Offers to save an unknown word after you type it
- Never learns from password or number fields, or when an app requests no personalised
  learning

**Panes**
- Symbols (two pages), an editing pane with cursor keys, select, copy/cut/paste and
  undo, a clipboard history, and an emoji picker
- Resizable on the fly, with an adjustable gap above the navigation bar

### Languages

| | Dictionary | Notes |
|---|---:|---|
| **English (US)** | 80,000 words | |
| **Spanish (Latin America)** | 80,000 words | Latin American vocabulary preferred over Peninsular; accents handled — `señor` is typed on the same keys as `senor` and offered correctly |

Both languages are enabled at the same time by default.

## Build

Requires JDK 17+ and the Android SDK with platform 36.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

./gradlew test testDebugUnitTest        # 297 tests, JVM only — no emulator needed
```

The prediction engine and its data model are pure Kotlin with no Android dependency,
which is why the whole engine test suite runs on the JVM in about a second.

### Rebuilding the dictionaries

The word lists are generated from the OpenSubtitles 2018 frequency data published by
[HermitDave/FrequencyWords](https://github.com/hermitdave/FrequencyWords):

```bash
cd tools
./fetch-wordlists.sh                                          # downloads the corpora
./build-dictionary.py --lang en --freq corpus/en_full.txt -o ../app/src/main/assets/dict/en.txt
./build-dictionary.py --lang es --freq corpus/es_full.txt --region 419 -o ../app/src/main/assets/dict/es.txt
```

Any `word<TAB>count` frequency list works as input. Adding a language is a new dictionary
file plus one entry in `TrieEngine.CONFIGURED_LANGUAGES`.

## Adding a prediction engine

The prediction backend is deliberately pluggable. Everything above the seam is
engine-agnostic:

```
T9InputMethodService        editor events, composing text, commits
        │
        ▼
interface InputEngine       ← :engine-api  (pure Kotlin, no Android)
        │
        ├── TrieEngine      ← :engine-trie  the built-in default
        └── YourEngine      ← :engine-yours
```

To plug in your own:

1. Create a module depending on `:engine-api`.
2. Implement [`InputEngine`](engine-api/src/main/kotlin/io/github/jcastell7/modernt9/engine/InputEngine.kt)
   and `EngineFactory`.
3. `include(...)` it in `settings.gradle.kts` and add it to `app/build.gradle.kts`.
4. Register its factory — one line in
   [`Engines.kt`](app/src/main/kotlin/io/github/jcastell7/modernt9/Engines.kt):

```kotlin
val factories: List<EngineFactory> = listOf(
    TrieEngineFactory(),
    YourEngineFactory(),   // ← that's the whole integration
)
```

Nothing else in the app names a concrete engine. It appears in the settings picker
automatically, and if its `initialize()` throws, the keyboard falls back to the default
rather than breaking. `EngineResources` is the only thing an engine may assume about its
host — an asset opener, a writable directory and a language tag — so an engine can be
developed and unit-tested on a desktop before it ever sees a phone.

## Licence and attribution

Modern T9 is free software, licensed under the
**[GNU General Public License v3.0 or later](LICENSE)**. You may use, study, share and
modify it; anything you distribute built from it must stay under the same licence.

Its dictionaries are derived from
[HermitDave/FrequencyWords](https://github.com/hermitdave/FrequencyWords) (OpenSubtitles
2018) and are licensed under
[Creative Commons Attribution-ShareAlike 4.0](https://creativecommons.org/licenses/by-sa/4.0/).

It is built with AndroidX, Jetpack Compose, Kotlin and `androidx.emoji2`, all under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

The same notices, with links, are shown to users in the app under **Settings → About &
licences** — the CC BY-SA attribution is a licence requirement, not just a courtesy, so
it lives where the people using the dictionaries can see it.
