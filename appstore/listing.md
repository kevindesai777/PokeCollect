# App Store listing

Drafts for App Store Connect, iOS app version 1.0. Paste the text inside each box as it is. The
counts are Unicode characters, counted by a script.

Adapted from the Play listing (removed in `2dc19b3`; `git show 2dc19b3^:play/listing.md`), with
these differences:
- No other platform or model is named (App Review 2.3.10): no Android, Google Play or Gemma. On
  iPhone the model is Apple's.
- No "no internet permission" (iOS has no such permission) and no "no analytics / no tracking"
  (analytics are planned, Kevin 2026-11-10).
- Says that questions need Apple Intelligence, because the App Store can't keep the app off other
  iPhones. Everything else works on any iPhone with iOS 26.
- "Pokémon" stays out of the keywords (2.3.7: no trademarks in keywords) and appears once in the
  body, then only in the disclaimer.

## Promotional text

Limit 170. **133 characters.** Can be changed at any time without a new version.

```text
Ask about your card collection in plain words, or read out the cards you just got. The AI runs on your iPhone, even in airplane mode.
```

## Description

Limit 4,000. **2,037 characters.** Ends with the disclaimer.

```text
PokeCollect tracks your Pokémon card collection and answers questions about it. The AI runs on your iPhone, so it works in airplane mode.

Ask in plain words
Type or say a question:
• "Which Gen 3 cards am I still missing?"
• "How many fire types do I have?"
• "Show me the water cards from Gen 1"
Apple's on-device model turns the question into a search. The app runs that search on your collection and shows you the answer, plus how it understood the question. The numbers come straight from your collection. The model never guesses them.

Say the cards you just got
Opened a few packs? Tap the mic and read the names out: "Gengar, Lapras, Scyther." Speech recognition runs on the iPhone too. The app matches each name it heard, flags any it isn't sure of and lets you pick the right one. Tap "Mark caught" and they're in. You can type the names instead.

Your collection in one list
• All 1,025 species, Gen 1 to Gen 9, one entry each
• Find any of them by name or National Dex number
• See how many you have from each generation
PokeCollect tracks which species you have. It doesn't track sets, printings or prices.

Private by design
• No account, no ads
• Your collection stays in a database on your iPhone
• Works in airplane mode
• The microphone listens only when you tap it, and the audio stays on your iPhone

What you need
• iOS 26 or newer
• For questions in your own words: an iPhone with Apple Intelligence turned on (iPhone 15 Pro or newer). On other iPhones the collection, search and adding cards by name all work.
• For voice: on-device speech recognition for English

Why I built it
PokeCollect is a demo of on-device AI that I built for my talk "Offline by Default". The code is open source under the Apache License 2.0.

PokeCollect is a free, unofficial fan app. It isn't affiliated with, endorsed or sponsored by Nintendo, Creatures Inc., GAME FREAK or The Pokémon Company. Pokémon and Pokémon character names are trademarks of their owners. Pokémon sprites © 1995–2026 Nintendo / Creatures Inc. / GAME FREAK inc.
```

## Keywords

Limit 100. **95 characters.** Comma-separated, no spaces. The name and subtitle are indexed
already, so their words aren't repeated here.

```text
card,tracker,collector,dex,checklist,tcg,offline,ai,voice,species,generation,binder,missing,gen
```

## Support URL

```text
https://github.com/kevindesai777/PokeCollect/blob/main/appstore/SUPPORT.md
```

Apple wants a page where people can reach you. `appstore/SUPPORT.md` (new) has the contact email
(the same one the Play listing and `play/DISCLAIMER.md` already show) and the issues link. It
works once this folder is pushed to `main`.

## Marketing URL

Optional.

```text
https://github.com/kevindesai777/PokeCollect
```

## Version

`1.0` (matches `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig`).

## Copyright

Limit 200.

```text
2026 Kevin Desai
```

Apple adds the © itself. The sprites' copyright is in the description's disclaimer.

## Routing App Coverage File

Leave empty. It's only for navigation apps.

## Other fields (App Information page)

- **Subtitle** (limit 30), **27 characters**: `Ask your collection offline`
- **Category:** Reference (what the live Pokéos and Dex 'Em All use); secondary: Utilities.
- **Privacy policy URL:** `https://github.com/kevindesai777/PokeCollect/blob/main/play/PRIVACY.md`.
  Covers both apps; its "On iPhone" section (added 2026-11-10) describes Apple's model and speech
  recognizer, iCloud Backup and deleting data. Live once pushed to `main`.
- **App Privacy:** Data Not Collected. Update it when analytics ship.
