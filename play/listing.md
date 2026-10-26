# Play store listing

Drafts for the Play Console main store listing. Paste the text inside each box as it is. The
counts are Unicode characters in the text inside each box, counted by a script ("•" and "é" count
as one each).

## App name

Limit 30. **11 characters.**

```text
PokeCollect
```

Kept as it is. There's no strong reason to change it: it's the app's name everywhere (the launcher,
the talk, the repo), and "Poke…" apps with an unofficial-fan-app disclaimer have lasted on Play at
1M+ installs. Don't add "Pokémon" or a keyword tail like "Card Tracker AI" to the title: the
impersonation policy looks at titles and icons that imply affiliation.

## Short description

Limit 80. **79 characters.**

```text
Offline AI for your card collection. Say the cards you got, ask what's missing.
```

Alternative, **77 characters**:

```text
Ask about your card collection by voice or text. On-device AI, fully offline.
```

Both keep "Pokémon" out of the short description, for the same reason as the title. The full
description says what the cards are.

## Full description

Limit 4,000. **2,534 characters.** Ends with the disclaimer.

```text
PokeCollect tracks your Pokémon card collection and answers questions about it. The AI runs on your phone. Nothing goes online: the app doesn't even have internet permission.

Ask in plain words
Type or say a question:
• "Which Gen 3 cards am I still missing?"
• "How many fire types do I have?"
• "Show me the water cards from Gen 1"
An AI model on your phone (Google's Gemma 3 1B) turns the question into a search. The app runs that search on your collection and shows you the answer, plus how it understood the question. The numbers come straight from your collection. The model never guesses them.

Say the cards you just got
Opened a few packs? Tap the mic and read the names out: "Gengar, Lapras, Scyther." Speech recognition runs on the phone too. The app matches each name it heard, flags any it isn't sure of and lets you pick the right one. Tap "Mark caught" and they're in. You can type the names instead.

Your collection in one list
• All 1,025 species, Gen 1 to Gen 9, one entry each
• Find any of them by name or National Dex number
• See how many you have from each generation
PokeCollect tracks which species you have. It doesn't track sets, printings or prices.

Private by design
• No account, no ads, no analytics, no tracking
• Your collection stays in a database on your phone
• Works in airplane mode
• The microphone listens only when you tap it, and the audio never leaves the phone

Getting the AI model
The model is 584 MB. Google Play downloads it right after you install the app. On mobile data it waits for Wi-Fi, unless you choose to download it now. Until it arrives, your collection works as usual. Ask needs the model.

What you need
• Android 12 or newer, on a 64-bit phone
• Space for the app and the 584 MB model
• For voice: on-device speech recognition for English (US). If your phone doesn't have it yet, Android downloads it once.

Why I built it
PokeCollect is a demo of on-device AI that I built for my talk "Offline by Default". The code is open source under the Apache License 2.0.

AI model terms
Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms. By using the app you agree to Gemma's use restrictions: ai.google.dev/gemma/prohibited_use_policy

PokeCollect is a free, unofficial fan app. It isn't affiliated with, endorsed or sponsored by Nintendo, Creatures Inc., GAME FREAK or The Pokémon Company. Pokémon and Pokémon character names are trademarks of their owners. Pokémon sprites © 1995–2026 Nintendo / Creatures Inc. / GAME FREAK inc.
```

## Release notes for version 1

Limit 500. **176 characters.**

```text
First release. Track your collection, ask about it in plain words, and add cards by voice. All on your phone, no internet needed once the AI model has arrived from Google Play.
```

## Other listing fields

- **Category:** Tools. (Entertainment is the other reasonable choice. Tools reads as a collector's
  utility, which fits the 18+ audience better.)
- **Contact email:** kevindesai777@gmail.com
- **Website:** the public repo, https://github.com/kevindesai777/PokeCollect (optional).
- **Privacy policy URL:** https://github.com/kevindesai777/PokeCollect/blob/main/play/PRIVACY.md
  (the same URL goes in App content > Privacy policy; it works once `play/` is pushed to `main`).
- **Icon, feature graphic, title:** our own drawings only. No sprite, Poké Ball or official logo. The
  icon is a dex-style device face (lens, lights, bezel), Kevin's choice on 2026-10-26.
  - App icon (512×512, 32-bit PNG): `play/graphics/icon-512.png`, from `scripts/make_icon.py`
    (the same spec draws the launcher icon).
  - Feature graphic (1024×500, 24-bit PNG): `play/graphics/feature-graphic.png`, rendered from
    `feature-graphic.html` in the same folder.
- **Screenshots:** Ask-screen shots (text answers, small or no sprites) and the collection list,
  rather than big artwork. They show sprites, so they're never committed: take them from a local
  build (1080×1920, 24-bit PNG; Play rejects a long side more than twice the short side, so a
  20:9 phone needs `adb shell wm size 1080x1920` first).

## Copy rules this follows

- Aimed at adult collectors: no "kids", "fun", "game" or "cute", no cartoon tone.
- Leads with offline, on-device AI, then voice input, then the collection.
- Says plainly that the app tracks species, not sets, printings or prices, so collectors know what
  they get.
- "Pokémon" appears once in the body and then only in the disclaimer. Three example names in the
  voice section, no keyword lists.
- No claims about speed, since none are measured on Play installs yet.
