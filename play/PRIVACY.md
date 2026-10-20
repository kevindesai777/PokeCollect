# PokeCollect privacy policy

*Last updated: 20 October 2026*

PokeCollect is a free app for keeping track of a Pokémon card collection. I'm Kevin Desai, and I
made it. This policy covers the PokeCollect app for Android, as distributed on Google Play.

## The short version

PokeCollect collects no data and shares no data.

Everything the app does happens on your phone. The app can't connect to the internet: it doesn't
have Android's internet permission. There are no accounts, no ads, no analytics and no crash
reporting. I never receive anything from the app.

## What stays on your phone

- **Your collection.** The cards you mark as caught are saved in a small database in the app's
  private storage on your phone. Nobody else can see it, me included.
- **Your questions.** When you ask something like "Which Gen 3 cards am I still missing?", an AI
  model on your phone turns the question into a search of that database. The question isn't sent
  anywhere, and the app keeps no history of what you asked.
- **Card data.** Names, numbers, generations and types are built into the app.

## The microphone

PokeCollect asks for the microphone (the `RECORD_AUDIO` permission) for one thing: voice input.
You tap the mic, then say a question or the cards you just got.

- The app listens only after you tap the mic, and stops when you tap stop.
- Your speech is turned into text by Android's own speech recognizer, in its on-device mode. The
  audio never leaves your phone. The app doesn't record or keep audio, and doesn't keep the text.
- If your phone doesn't have the on-device English speech model yet, the app asks Android to
  download it. Android's speech service does that download, not PokeCollect.
- You can say no to the microphone. You can type everything instead.

## The AI model

The Ask feature uses Gemma 3 1B, an AI model made by Google, and it runs entirely on your phone.

Google Play delivers the model as part of the app (a 584 MB "AI pack"), usually right after you
install it. That download is between your phone and Google Play, just like installing or updating
an app, and Google's own privacy policy covers it. PokeCollect itself sends and receives nothing.

## Permissions

- **Microphone** (`RECORD_AUDIO`): voice input, as described above.
- **Google Play's model delivery.** Google's Play delivery and WorkManager libraries, which
  unpack the model after Google Play downloads it, add these: run a foreground service for data
  sync (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`), view network connections
  (`ACCESS_NETWORK_STATE`, to tell whether you're on Wi-Fi), keep the phone awake while unpacking
  (`WAKE_LOCK`), and resume after a restart (`RECEIVE_BOOT_COMPLETED`). None of them let the app
  connect to the internet.

That's the full list. The app has no internet permission and no advertising ID permission.

## Backups

If you have Android backup turned on for your Google account, Android may include your
collection database in that backup, and nothing else from the app. The same goes for moving to a
new phone with a device-to-device transfer. Google runs that backup under your backup settings. I
never see it.

## Deleting your data

Uninstall PokeCollect and your collection is deleted from the phone. To wipe it without
uninstalling, go to your phone's Settings, then Apps, then PokeCollect, then Storage, and clear
storage. If Android backed it up, you can delete that backup in your Google account's backup
settings.

I don't hold any of your data, so there's nothing to delete on my side.

## Children

PokeCollect is made for adult collectors. It isn't directed at children, and it collects no data
from anyone, children included.

## Changes

If this policy changes, I'll update this page and the date at the top.

## Contact

Questions about this policy: kevindesai777@gmail.com
