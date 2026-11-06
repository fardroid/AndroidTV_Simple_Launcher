# Changelog

## 1.2.0

In step with [Simple LG launcher](https://github.com/fardroid/Simple-LG-launcher) 2.0.0, where it applies to Android. What is
left out is LG's own: the ways of opening on Home, the quick TV settings, the Homebrew Channel and root, the installer, the
Magic Remote pointer.

- **The home screen shows a change at once.** Change a look setting (icon size, margins, card colour, frost, the clock's font, size,
  weight and colour, the date, the temperature unit) and Settings steps aside for two seconds: the home screen shows the change,
  with the setting's name and value in a bar at the top. ← → go on changing, ↑ ↓ or Back bring Settings back.
- **New icon and banner**: a roof over four tiles, as in Simple LG launcher 2.0. The launcher's and the box's settings tiles in the
  row are coloured as there (a purple one with the launcher's mark, a grey one with a gear), the update tile is green, and the
  city, calendar and feed pages have the same glyphs before their rows.
- **A new app ID, `tld.my.simpletv`** (it was `tld.my.hearth`), and the code's package is the same. This is a new app for the box:
  the settings of the old one do not move over. Install it, set it up again, choose it as the home app and switch the Home button
  helper on, then uninstall the old one (`adb uninstall tld.my.hearth`).
- **Cards in columns.** The clock and the weather, then the calendar or the news, with the quote of the day under them. With the
  calendar and the news both on, three columns of 560 px (the clock puts the date under the time, the weather shows five days).
  ← → go from card to card; ↑ from a card with nothing above it goes to the next column. The placing is checked against the same
  cases as the webOS version's (`BoardLayoutTest`).
- **News** (Settings → Widgets → News): headlines from Google News for a chosen country (52 editions; in the launcher's language
  when the country has one, else in the country's own) and from up to eight RSS or Atom feeds of your own, in any encoding the feed
  declares, newest first, without repeats, the last two days only. ← → turn the pages, ↑ ↓ choose a headline, OK shows it over the
  screen with its description and a QR code of its link (ZXing core, Apache 2.0).
- **Quote of the day** (Settings → Widgets): from Wikiquote in the launcher's language. The editors' quote of the day where there
  is one (en, es, pl, zh, ko, tr, pt, uk; the Italian one from its feed); otherwise a quote picked by the date from one of about
  400 writers, poets, thinkers and scientists born before 1920 (a Wikidata query, kept for a month). The text is set as large as the
  card allows.
- **Calendar.** All the day's events in a list that scrolls an event at a time with ↑ ↓ and fades at an edge with more beyond it;
  ← → on the events go to the cards beside; above the first event the head (the date and its arrows) takes ← → to change the day
  or the month.
- **Wallpaper gallery** (Settings → Wallpaper → Picture): the built-in pictures, the launcher's own folder and, once the user
  allows it (the system asks), the pictures on the box by their folders, as tiles with previews, five across. OK ticks pictures for
  the slideshow; with none ticked all the built-in and own ones show, as before.
- **New versions** (Settings → Home screen → About, on by default): once a day the launcher asks GitHub for the latest release. A
  newer one shows as a note and an *Update* tile whose OK shows the release with a QR code of its page. Nothing is downloaded or
  installed (Android installs only what the user allows).
- The texts of all of it are in the thirteen languages (the webOS version's translations; the few texts of the Android port's own,
  such as the gallery's, are machine-translated).

## 1.1.0

In step with [Simple LG launcher](https://github.com/fardroid/Simple-LG-launcher) 1.9.2, where it applies to Android.

- **Thirteen languages.** Spanish, French, German, Italian, Portuguese, Turkish, Ukrainian, Chinese (Simplified), Japanese,
  Korean and Polish join English and Russian (Polish thanks to [DriftEngineer](https://github.com/DriftEngineer)). *Same as the
  box* picks the box's language when the launcher has it, English otherwise. The texts are Android resources
  (`res/values*/strings.xml`, by id, with `<plurals>` for counted nouns), translated from the webOS version's dictionaries
  with the Android port's own texts added; the chosen language opens the app's resources in its locale, whatever the box
  is set to. Dates, month and weekday names come from the platform; the city search answers in the chosen language.
  A unit test checks that every text is in every language.
- **Pixel shift (OLED)** (Settings → Appearance, on by default): the clock, the cards and the apps move a few pixels once a
  minute round a loop of eight places.
- **App names: all, the selected one only, or none** (Settings → Appearance → App names). With the selected one only, the
  others keep their room, so nothing moves.
- **Frost** (Settings → Appearance, under Frosted glass): how much the glass blurs the picture behind it, 100% (as before)
  down to 30%. A change remakes the glass of the picture on screen at once.

## 1.0.0

The first public version, in step with [Simple LG launcher](https://github.com/fardroid/Simple-LG-launcher) 1.9.0.

- A native Android TV app (Kotlin, Jetpack Compose) with the webOS version's home screen: the clock and date, the
  weather with an hourly curve and seven days, the calendar with a day strip and a month view, frosted-glass cards and
  one row of apps over a slideshow of wallpapers.
- Settings in the webOS version's look: the sections on the left (Apps, Appearance, Widgets, Wallpaper, Home screen),
  the chosen one on the right in rounded groups; the apps as a grid of tiles.
- Widgets: one font for the clock, the date and the cards (Inter, Poppins, Oswald, Roboto Mono, Cormorant or the box's
  own); clock weights from thin to bold; the cards turn light or dark together.
- Launcher by default says whether the Home button really opens the launcher, and opens the screen that still needs a
  change (the default home app, or the Home button helper that Google TV needs).
- The weather and calendars try again after 20 seconds when the box is not online yet.
- Thirteen wallpapers built in. English and Russian.
