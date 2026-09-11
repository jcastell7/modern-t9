# Privacy Policy — Modern T9

**Modern T9 does not collect, transmit or share any data.** This document explains what
that means concretely, what the app stores on your device and why, and the two places
where data could leave the device through Android itself rather than through the app.

Last updated: 2026-09-11. Applies to all versions from 1.0.0.

---

## The short version

- The app has **no internet permission** and contains **no network code**.
- There are **no analytics, no advertising, no telemetry and no crash reporting**.
- Nothing you type is sent anywhere. Nothing is shared with anyone, including the
  developer.
- What the app learns about your writing is stored **only on your device**, in plain
  text files you can read and delete.

## What a keyboard necessarily sees

A keyboard receives everything you type into it — messages, searches, and passwords
alike. That is what a keyboard is. What matters is what happens next, and in Modern T9
the answer is: it is used to compose the text you asked for, and some of it is used to
improve predictions on your device. None of it is transmitted.

## What is stored on your device

All of the following live in the app's private storage, which other apps cannot read.

| What | Where | Why |
|---|---|---|
| Words you have typed, with a count | `learned-words.tsv` | So your own vocabulary ranks higher |
| Pairs of consecutive words | `learned-bigrams.tsv` | Next-word prediction |
| Phrases you saved (emails, URLs) | `user-phrases.tsv` | So they can be typed from a few keys |
| Settings: chosen engine, language, keyboard size | app preferences | To remember your choices |

These are ordinary text files. You can inspect them, and deleting them resets the
keyboard's learning without affecting anything else.

### What is deliberately *not* learned

The keyboard does not learn from:

- **Password fields** of any kind, including numeric PINs
- **Number and phone-number fields**
- Any field where the app you are typing in has asked for no personalised learning
  (Android's `IME_FLAG_NO_PERSONALIZED_LEARNING`, used by private and incognito modes)

## Clipboard history

The editing pane offers a list of recently copied text. This list is held **in memory
only** — it is never written to disk, is capped at 25 entries, and disappears when the
keyboard process ends. Clipboard contents are frequently sensitive (passwords, codes,
addresses), which is why they are not persisted. You can also clear it or remove single
entries from within the keyboard.

The keyboard only observes the clipboard while it is the active input method.

## Permissions

The app declares **no permissions**. In particular it does not request internet,
contacts, location, phone state, SMS, camera, microphone or storage access.

## Two things to know about Android itself

The app sends nothing. But two Android mechanisms operate around it, and honesty
requires naming them.

### 1. Android backup

Android's auto-backup can copy an app's data to the Google account signed in on the
device, and its device-transfer tool can move it directly to a new phone. Modern T9
restricts both to **settings only** — the chosen engine, language and keyboard size.

Your personal dictionaries — learned words, learned bigrams and saved phrases — are
**explicitly excluded** from cloud backup and from device-to-device transfer. They never
leave the phone by either route. The rules are in
`app/src/main/res/xml/backup_rules.xml` (Android 8–11) and
`app/src/main/res/xml/data_extraction_rules.xml` (Android 12+).

The consequence is deliberate: on a new phone the keyboard starts with your settings but
not your writing habits, and relearns them as you type.

### 2. The emoji font

The emoji picker uses Android's `emoji2` library. On startup it asks the device's
*downloadable font provider* — on most phones, Google Play Services — whether a newer
emoji font is available, so that recent emoji render correctly. This is a request for a
font file, made through a system component; **no text you have typed is involved**, and
the app itself has no network access. On devices without Google services it simply falls
back to the built-in font.

## Third parties

No third party receives any data from this app. It contains no SDK from any advertising,
analytics or tracking vendor.

## Data you can delete

- **Everything:** uninstall the app, or clear its storage in Android's app settings.
  Because the dictionaries are excluded from backup, uninstalling really does remove
  them — there is no cloud copy to restore.
- **Learned words and phrases only:** delete the three `.tsv` files listed above, or
  clear the phrases from Settings → My phrases.
- **Clipboard history:** clear it from the clipboard pane, or simply close the keyboard.

## Children

The app collects no data from anyone, so no special provisions apply to children.

## Changes

If a future version changes any of the above, this document will be updated and the
change noted in the release notes. The date at the top reflects the last revision.

## Verifying these claims

Modern T9 is free software under the GPL-3.0-or-later. You do not have to take this
document's word for it: the absence of an internet permission is visible in
`app/src/main/AndroidManifest.xml`, and the storage behaviour described above is in
`engine-trie/` and `app/src/main/kotlin/`. Reproducible builds are on the roadmap so
that a downloaded APK can be checked against the source.

## Contact

Questions about this policy: open an issue on the project's repository.
