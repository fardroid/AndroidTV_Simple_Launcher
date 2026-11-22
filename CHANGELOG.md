# Changelog

## 1.3.1

In step with [Simple LG launcher](https://github.com/fardroid/Simple-LG-launcher) 2.2.7.

- **Liquid glass with wider, stronger edges**, picked on a page of sliders: archisvaze/liquid-glass's default. The cards' corners
  now round by 60 px instead of 36, with every kind of glass, so that liquid glass's bevel, never wider than the corner's radius,
  can be 60 px wide; the glass is 80 px thick (was 60). The bending is plain to see now, where it was barely there.
- **Card background: Neutral** (Settings → Appearance → *Card background*): no colour, only the glass.
- **The shadow under the cards is even**, as dark as it is at the cards' sides: one that thinned out towards the edges, as a real
  shadow does, made the edges look lit, like sanded glass.

## 1.3.0

In step with [Simple LG launcher](https://github.com/fardroid/Simple-LG-launcher) 2.2.6, where it applies to Android.

- **Glass: off, frosted or liquid** (Settings → Appearance → *Glass*, in place of the *Frosted glass* switch). Liquid glass is a
  lens: over a bevel as wide as the cards' corners round, the picture is bent inwards and stretched out to the edge, each colour
  a little differently, the way the web's liquid glass demos work it out (the ray through the tilted surface is bent, index 3,
  and goes down through the glass's height there). No light is drawn on it, only the bending. Android draws its own shaders
  only from Android 13, and the boxes are often older, so the bevel is worked out once for each picture and card, in the
  background, as a picture of its own.
- **Frost goes down to off.** With *Frost* off the glass does not blur at all: clear glass, the picture at full size.
- **A screen saver of the launcher's own** (Settings → Wallpaper → *Screen saver*: off, or after 2 to 60 minutes; 5 at first).
  After that long without a key, the app row sinks and fades and the cards fade a beat later (Settings fades whole); the
  wallpaper stays alone, slowly zooming and drifting, and a slideshow changes its picture every 30 s. Any key brings back what
  was there and does nothing else (OK does not open the tile in focus; Home only wakes it). The box's own screen saver (Ambient
  mode) is not touched: when it comes, this one ends under it.
- **Banners for the launcher's messages**: the cards' glass at the top of the screen, over any screen, with a round icon (blue for
  something going on, green for done or a new version, red for an error) and a ring that runs down until it goes. Several stand
  one under another, the newest at the top.
- **Updating from the Update tile.** It downloads the release's APK from GitHub, checks it against the size and SHA-256 GitHub
  gives for it, and hands it to Android's installer, which asks you to confirm and starts the new version. The first time,
  Android has to be told that the launcher may install apps: the tile opens that page (Install unknown apps) and a banner says
  what to do. The launcher now asks for that permission (`REQUEST_INSTALL_PACKAGES`); nothing is installed without your OK. A
  new version also shows a banner once.
- **The wallpaper collection** (Settings → Wallpaper → Picture): the photos of Simple LG launcher's collection (mountains, nature,
  space, abstract) as folders of the gallery, from their small previews. A photo ticked there is downloaded in the background,
  checked against its SHA-256, and joins the slideshow when it has come; a banner says how many are still coming. Photos no
  longer ticked are deleted.
- **Dim the picture** (Settings → Wallpaper → Brightness): off, at night (from sunset to sunrise in the weather's city, worked out
  on the box; with no city, 19:00 to 7:00) or always, by 10 to 70 %. The cards' glass and colour follow it.
- **The cards' colours.** *Card colour* is now *Card background*; Auto is light or dark, nothing in between; the light one takes
  the colour theme's hue too, so a theme shows on the cards in both looks.
- **The clock has the colour of the rest of the text on the cards**, the theme's; its own colour setting is gone. The clock's
  weight moved to Appearance → Widgets (*Clock weight*), and the date's rows joined the clock's group in Widgets.
- **The picture behind a card in focus stays still** while the card grows.
- **The weather tries three Open-Meteo hosts** in turn, starting with the one that answered last.
- **The frost grows evenly.** The glass was a quarter-size copy of the picture, which stretched over the screen already blurred it
  about as much as 40 %; it is now a half-size copy, blurred by up to 14 px as the square of the setting (as in Simple LG
  launcher), so 10 % is nearly clear and 60 % and 100 % differ.
- **The glass lines up with the picture behind it** while the pixel shift has moved the cards (it was a few pixels off, now and
  then).
- **The cards' shadow falls under them too**, on the picture behind the glass: a light card no longer sits in a dark frame.
- **A card in focus** grows with its liquid glass worked out for its grown size, instead of the resting one stretched.
- **Changing a look setting shows the row where it is**, on a soft halo of Settings' own colour, while the rest of Settings
  fades and the home screen shows the change (as in Simple LG launcher), instead of a bar at the top.
- **A new version also shows as a line over the row**, with a green icon; the row moves down to make room for it (as far as the
  bottom margin allows, then the cards move up).

## 1.2.0

In step with [Simple LG launcher](https://github.com/fardroid/Simple-LG-launcher) 2.0.0, where it applies to Android. What is
left out is LG's own: the ways of opening on Home, the quick TV settings, the Homebrew Channel and root, the installer, the
Magic Remote pointer.

- **The home screen shows a change at once.** Change a look setting (icon size, margins, card colour, frost, the clock's font, size,
  weight and colour, the date, the temperature unit) and Settings steps aside for two seconds: the home screen shows the change,
  with the setting's name and value in a bar at the top. ← → go on changing, ↑ ↓ or Back bring Settings back.
- **Colour themes** (Settings → Appearance, the first row): Evening sky, OLED black, Calm blue-grey, Forest, Ocean, Sunset, Lavender
  and Graphite, as in Simple LG launcher. A theme sets the colours of the text, the app tiles, the dark cards, Settings' background
  and the plain background used without a picture. Evening sky is the default, so the look of a fresh install changes a little.
- **Settings in the same order as in the webOS app**: Font moved to Appearance → Widgets (the group was called Cards), Picture comes
  before the slideshow rows, and About lists the version first.
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
