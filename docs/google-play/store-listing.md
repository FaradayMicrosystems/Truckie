# Google Play: listing and Play Console answers

Copy-paste text for Play Console. Character limits are Play's.

## App details

- **App name** (30): `Truckie`
- **Short description** (80): `Your day's stops in order. One tap per drop. Maps or Waze does the driving.`
- **Category:** Maps & Navigation. **Type:** App. **Free.**
- **Contains ads:** No.

### Full description (4000)

```
Truckie keeps your delivery and pickup stops in order and hands Google Maps or Waze one stop at a time, so the map never shuffles your run or inserts stops mid-route.

• Add stops by address, coordinates or a pasted Google Maps link. New stops always go on the end.
• Hold and drag to reorder, or tap Optimize to chain the stops nearest-next by road.
• One tap per stop: "That one's done! Let's ride!" strikes it off (restorable) and navigation to the next stop opens after 5 seconds.
• Optional auto-complete on arrival: stop at the next drop for the time you choose and Truckie ticks it off for you.
• Android Auto: next stop, done, navigate and undo on the car screen.

No account, no ads, no tracking. Your stops stay on your phone.
```

## App content (Policy > App content)

- **Privacy policy URL:** needs a public link. The repo is private, so paste
  `docs/google-play/privacy-policy.md` into a public page (for example a Google Doc set to
  "Anyone with the link", or a GitHub Gist) and use that link. Fill in your contact email first.
- **App access:** All functionality is available without special access.
- **Ads:** No ads.
- **Content rating:** questionnaire, category "All other app types"; answer No to everything. Result: Everyone / PEGI 3.
- **Target audience:** 18 and over. Not appealing to children.
- **News app:** No. **Government app:** No. **Financial features:** none.
- **Health apps:** No.

### Data safety

- Does the app collect or share user data? **Yes** (location and addresses go to the address search and routing services).
- Is all data encrypted in transit? **Yes** (HTTPS).
- Can users request deletion? **Yes** (Clear all stops, or uninstall).
- Data types:
  - **Location > Precise location** and **Approximate location:** Collected, not shared. Processed
    ephemerally: **Yes**. Required. Purpose: **App functionality**.
  - **Personal info > Address** (addresses you type): Collected, not shared. Processed ephemerally: **Yes**.
    Required. Purpose: **App functionality**.
- Everything else: not collected.

### Permissions declarations Play asks about

- **Foreground service: location** (`FOREGROUND_SERVICE_LOCATION`). Task type: **Navigation / user-initiated location**.
  Description: "When the driver turns on Auto-complete on arrival, Truckie watches GPS while Google Maps is
  on screen to tick off the next delivery stop once the driver has stopped there. A notification shows
  the whole time, with a Turn off button." Play asks for a short video link: record the phone screen
  turning on Auto-complete, the notification appearing, and a stop ticking off (an unlisted YouTube link is fine).
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`): used only for "Open Maps by itself" (opens
  navigation to the next stop hands-free). Off until the driver turns it on.
- Not in the Play build: `REQUEST_INSTALL_PACKAGES` (the GitHub updater is removed from Play builds).

## Advanced settings > Form factors

Add form factor **Android Auto**. Truckie declares the car app category `POI` and hands navigation to the
car's navigation app. Car app quality review does not block the **internal testing** track; it becomes
blocking only for open testing and production.

## Graphics (only needed before open testing or production)

- App icon 512 x 512 PNG, feature graphic 1024 x 500, at least 2 phone screenshots.
- Android Auto screenshots for the car listing.
