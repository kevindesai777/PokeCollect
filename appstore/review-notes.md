# App Review notes

Answer to the 2026-11-11 rejection of 1.0 (2): Guideline 2.1, Information Needed. Apple asked for
six things in the **Notes** field of App Review Information, plus a screen recording. Paste the
box below into Notes (limit 4,000), attach the recording to the reply in App Review, then resubmit.

Item 6: Kevin chose to keep the sprites (2026-11-11), so the box below already has 6b. If Apple
then rejects under 5.2.2 (intellectual property), switch to a sprite-free iOS build, new
screenshots and 6a.

## Notes

```text
1. Screen recording
Attached to my reply in App Review. Recorded on an iPhone 16e with iOS 26.6, starting from launch. The app has no accounts, no user-generated content and no paid content or purchases.

2. Purpose and audience
PokeCollect is a checklist for people who collect Pokémon trading cards and want one card of every species. It lists all 1,025 species (Gen 1 to 9); you tick off the ones you have and see your progress overall and per generation. Collectors use it at card shops, trades and events, often with no signal, so everything runs on the iPhone and works in airplane mode. Two helpers make it faster: ask questions about your collection in plain words ("Which Gen 3 cards am I still missing?"), and read out the cards you just pulled from a pack to tick them off at once. The app is free, with no account, ads or purchases.

3. How to use the main features
No login, setup or sample files are needed. Open the app and:
- Collection: scroll or search by name or number ("pikachu", "25") and tap the box to mark a species caught. The blue card shows overall progress; "By generation" shows each generation.
- Ask: tap Ask at the top, type a question and tap the arrow, e.g. "Which Gen 3 cards am I still missing?" or "How many fire types do I have?". The answer lists the matching species and shows how the question was understood. This needs Apple Intelligence turned on (iPhone 15 Pro or newer); on other iPhones the Ask screen says so and everything else works.
- Voice: tap the yellow mic, allow microphone and speech recognition, and say names, e.g. "Gengar, Lapras, Scyther". Tap stop, check the list (unclear names are flagged with choices), then tap "Mark N caught". English on-device speech recognition must be available.
- About (the "i" button next to the title): licences and credits.

4. External services
None. The app makes no network connections and has no server. It uses two Apple frameworks that run on the device: Foundation Models (Apple's on-device model, which turns a question into a structured search; the counts come from the app's own database, not from the model) and Speech (SFSpeechRecognizer with requiresOnDeviceRecognition). Species names, numbers, generations and types are bundled in the app, built from the open PokéAPI dataset. No third-party SDKs for analytics, ads or AI.

5. Regional differences
The app is the same in every region. The only difference comes from Apple: questions need Apple Intelligence, which isn't available in every region or language; where it isn't, the Ask feature is unavailable and the rest of the app works. The app's text and voice input are in English.

6. Third-party material
PokeCollect is an unofficial, free fan app and isn't affiliated with or endorsed by Nintendo, Creatures Inc., GAME FREAK or The Pokémon Company; this is stated in the description and the About screen. Next to each species the app shows a small classic in-game sprite, credited "© 1995–2026 Nintendo / Creatures Inc. / GAME FREAK inc." I don't hold a licence for these sprites. Names, numbers, generations and types are factual data. The app icon and all other graphics are my own.
```

### 6a: sprite-free build

```text
PokeCollect is an unofficial, free fan app and isn't affiliated with or endorsed by Nintendo, Creatures Inc., GAME FREAK or The Pokémon Company; this is stated in the description and the About screen. The app contains no artwork, sprites, logos or other images from the games or cards: each species is shown by its name, National Dex number, generation and type, which are factual data. The names are used only to identify the species a collector owns. The app icon and all graphics are my own.
```

### 6b: current build with sprites

```text
PokeCollect is an unofficial, free fan app and isn't affiliated with or endorsed by Nintendo, Creatures Inc., GAME FREAK or The Pokémon Company; this is stated in the description and the About screen. Next to each species the app shows a small classic in-game sprite, credited "© 1995–2026 Nintendo / Creatures Inc. / GAME FREAK inc." I don't hold a licence for these sprites. Names, numbers, generations and types are factual data. The app icon and all other graphics are my own.
```

## Screen recording

Apple wants it on a physical device with the latest iOS, starting at launch. Record on the iPhone
(Control Center → Screen Recording; add it in Settings → Control Center if it's missing). Turn on
the microphone in the recording control so the voice step is heard. About 2 minutes:

1. Start recording on the Home Screen, then turn on airplane mode in Control Center.
2. Tap the PokeCollect icon (the recording must begin with the launch).
3. Scroll the collection; open "By generation" and show the progress.
4. Search "pikachu", tap the box to mark it caught; the count goes up. Clear the search.
5. Ask: tap Ask, type "Which Gen 3 cards am I still missing?", tap the arrow, show the answer and the "how it
   understood" part. Ask one more, "How many fire types do I have?".
6. Voice: tap the mic, allow both permissions if asked, say "Gengar, Lapras, Scyther", stop,
   show the list, tap "Mark 3 caught".
7. Go back, tap the "i" next to the title and scroll through the credits and licences.
8. Stop recording.

The video lands in Photos. Attach it to the reply on the rejection message in App Store Connect
(the paperclip in the reply box); if it's too large, trim it in Photos first.
