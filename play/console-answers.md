# Play Console answers

Ready-to-paste answers for Policy > App content, plus the reasons behind them. Console wording
changes over time: where a label here doesn't match the screen, pick the option with the same
meaning. Text inside a box is for pasting.

Fill these in this order: privacy policy, ads, app access, then the rest. Target audience can't be
started until the first three are done.

## Privacy policy

- **Privacy policy URL:** https://github.com/kevindesai777/PokeCollect/blob/main/play/PRIVACY.md

The app's About screen links the same URL. It only works once `play/` is committed and pushed to
`main`.

## Ads

- **Does your app contain ads?** No, my app does not contain ads.

## App access

- **All functionality is available without any access restrictions.** No sign-in, no account, no
  subscription.

Nothing needs instructions for reviewers. The Ask screen needs the on-device model, which Google
Play delivers after install, like for any user.

## Content rating (IARC questionnaire)

- **Email address:** kevindesai777@gmail.com
- **Category:** Utility, Productivity, Communication, or Other. It's a collection tracker, not a
  game, social app, store or news app.
- **Every content question: No.** No violence, blood, sexual content, nudity, crude humour or bad
  language, fear or horror content, drugs, alcohol or tobacco, gambling or simulated gambling.
- **Does the app let users interact or exchange content?** No. There's no chat, no sharing, no
  user-generated content that others can see.
- **Does the app share the user's current location?** No.
- **Does the app allow purchases of digital goods?** No. No in-app purchases.
- **Does the app give unrestricted internet access (a web browser or search engine)?** No. The
  app has no internet permission.
- **If asked about AI or generated content:** the on-device model never writes free text for the
  user. Its output is a small structured query (JSON), or a choice of card name from a few
  candidates. The answer the user reads is written by plain code from database counts.

The sprites don't change any of these answers. They should give the lowest rating in most
regions.

## Target audience and content

- **Target age groups:** Ages 18 and over. Only that one.
  - Any group under 13 brings in the full Families policy. Declaring 13+ doesn't settle it
    either: Play compares the declared audience with the app and the listing, and "youthful
    animation or young characters" can get an app rejected. 18+ alone, with a collector-focused
    listing, is the safest choice.
- **Restrict Minor Access** (Google's help page says this checkbox appears when only 18+ is
  selected): going by the name, it limits the app for users Play believes are minors. I couldn't
  confirm from Google's docs exactly what it does, so it's your call. No other answer here
  depends on it.
- **If asked whether the store listing could unintentionally appeal to children:** No.

```text
PokeCollect is a collection tracker and on-device AI demo for adult card collectors. The listing
is written for collectors: an original icon and feature graphic with no Pokémon artwork, no kid
keywords, and screenshots of the Ask screen and the collection list rather than big artwork. The
app shows small classic sprites next to each species in the list. It has no game, no characters
to play with, and no content made for children.
```

## Data safety

- **Does your app collect or share any of the required user data types?** No.

That ends the data questions: encryption in transit and data deletion are asked only when the
answer is yes. Check the preview: it should say "No data collected" and "No data shared with third
parties".

Why "no" is right:

- Play defines "collect" as sending data off the device. Data handled only on the phone doesn't
  count.
- The app has no internet permission, so it can't send anything. No account, no ads, no analytics,
  no crash reporting, and no third-party SDK that sends data.
- The microphone, the speech recognizer and the AI model all run on the phone. The collection stays
  in a local database.
- Google Play downloads the AI pack. Play sends device details and the app version to Google for
  that, as it does for every install and update. That's Google Play's own processing, not the
  app's. (A judgment call, but a defensible one.)
- Android backup, if the user turned it on, is Google's backup service under the user's settings.
  The app never sends or reads that copy.

## Advertising ID

- **Does your app use advertising ID?** No.

Before saving, check that the merged manifest of the release build has no
`com.google.android.gms.permission.AD_ID`. If it shows up, find the library that adds it before
answering.

## Government apps

- **Is your app developed by or on behalf of a government?** No.

## Financial features

- **My app doesn't provide any financial features.**

## Health

- **My app doesn't have any health features.**

## News apps

- **Is your app a news app?** No.

## COVID-19 contact tracing and status apps

- **My app is not a publicly available COVID-19 contact tracing or status app.**

## Foreground service permissions

This form appears only after a bundle with the permission is uploaded. The permission is
`FOREGROUND_SERVICE_DATA_SYNC`, merged in from Google's `asset-delivery` library (used through
`ai-delivery`). The app starts no foreground service itself.

- **Foreground service type:** Data sync (`dataSync`)
- **Use case:** Network transfer: Upload or download. (Avoid "Other": Unity developers using Play
  Asset Delivery were rejected after choosing "Data Sync – Other" with a video that didn't match.)

**Description of the feature:**

```text
PokeCollect does not start any foreground service itself. The dataSync foreground service in the
manifest is Google's Play Asset Delivery extraction service, added by the
com.google.android.play:asset-delivery library (used through com.google.android.play:ai-delivery).

The app ships its on-device AI model (Gemma 3 1B, 584 MB) as a fast-follow AI pack. After the
app is installed or updated, Google Play downloads the pack and this service unpacks it into the
app's storage. Nothing else uses it.

Google Play's policy exempts "foreground service type dataSync only when using Play Asset
Delivery features". This is that case.
```

**Impact if the task is deferred or interrupted:**

```text
The model isn't ready, so the Ask feature (questions about the collection, and voice input that
needs the model) is unavailable until the 584 MB model arrives. The app shows that it is getting
the model. The rest of the app, the collection list, keeps working.
```

**Video link:** [unlisted YouTube link]

What the video shows, in one take:

1. Install PokeCollect from Google Play (or a `bundletool --local-testing` install, if internal
   testing isn't approved yet).
2. Open the app while the model is still on its way: the "getting the model" progress shows.
3. The download and unpacking finish.
4. Open Ask, type or say "Which Gen 3 cards am I still missing?", and the answer appears.

If the phone is on mobile data, include tapping the download button and confirming the dialog:
that shows the user starting the download, which matches the "Upload or download" use case.
