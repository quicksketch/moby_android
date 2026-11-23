# Moby for Android

An Android app for posting to a Backdrop CMS site that runs the
[Moby (`moby`) module](https://github.com/backdrop-contrib/moby). You can log in, create posts with a description, date,
location, and photos and videos, browse recent posts, and edit them.

The app talks only to the moby REST API. See
[API.md](https://github.com/quicksketch/moby_docs/blob/main/API.md) in
[moby_docs](https://github.com/quicksketch/moby_docs) for the full API.

[SPECIFICATIONS.md](https://github.com/quicksketch/moby_docs/blob/main/SPECIFICATIONS.md) describes everything the app does. It is the
specification both the Android and iOS apps follow, so they stay
feature-equivalent.

## Status

This is an early mock. Everything the app uses is implemented on the server:
discovery, login, uploads, the feed, and creating and editing posts.

## Features

- **Sites.** The app opens on a list of sites, each with its favicon and
  whether you are logged in to it. Each site keeps its own login and its own settings, so you
  can stay logged in to several. Logging out keeps the site in the list, and
  tapping it logs in again. Its menu also removes the site.
- **Logging in.** Enter the site's address. The app reads the site's
  discovery document (`GET /api/moby?api=N`) and builds the login form from the
  site's authentication mechanism, so other mechanisms work without app
  changes. The API token is encrypted with an Android Keystore key.
- **Sharing from other apps.** Photos and videos shared to the app from any
  other app open in a new post. If you are logged in to exactly one site, they
  go to that site. Otherwise the list of sites asks which one to post to.
- **Creating and editing posts.**
  - Photos and videos are uploaded as soon as they are picked, each with its
    own progress bar. Before uploading, they are checked against the site's
    allowed extensions and size limits, including the separate video limit.
  - Videos larger than the site's limit are compressed to about 1080p, or
    720p if needed, using an estimate to choose before any encoding. If 720p
    wouldn't fit, the video is rejected. Videos over 200 MB that the site would
    accept anyway offer a choice to compress them or upload the original.
  - Each item has an Alt button for its alt text. Tapping an item shows the
    photo or plays the video full screen. Touch and hold an item to drag it
    into a new place; screen readers offer "Move earlier" and "Move later"
    actions instead. Removing an upload that isn't attached to a post yet
    deletes it from the site.
  - The date and time are sent as ISO 8601 with the device's UTC offset.
  - The location can be typed, or filled from the device's location
    (without Google Play services). It is shown only if the site maps it.
  - Tags are added in a field that suggests the site's existing tags as you
    type (`GET /tags`). Enter, a comma, or a suggestion adds a tag. Hashtags in
    the description, like `#hiking`, are shown as tags too, found by the same
    rules as the site (`data/Hashtags.kt`); the site adds them itself, so
    removing a hashtag from the description removes its tag. Shown only if
    the site supports tags.
  - Users who may publish choose between published and draft. Other users can
    choose to save a draft.
  - Each new post has a `client_id`, so retrying after a timeout never creates
    a duplicate.
- **Descriptions.** When the site's description format is HTML
  (`description.html` in discovery), what the user types is HTML-encoded
  before it is sent, as the node form does. Descriptions are always shown as
  plain text in the feed and editor, never as HTML. Posts written on the site
  that contain markup are edited as HTML, so their formatting isn't lost.
- **Feed.** Recent posts, with pull to refresh and paging. Tap a post to open
  it. If the token is revoked, the app returns to the list of sites.
- **Post page.** The post's real page, as rendered by the site for the logged-in
  user, so drafts can be viewed. A floating toolbar in the lower right offers
  edit and delete, if the user is allowed to. Links on the page open in the
  browser. The token is only ever sent to the site itself.

## Building

You need the Android SDK. The easiest way to get it is to install Android
Studio and run its setup wizard. The build uses Android Studio's bundled JDK.

```
export JAVA_HOME=/snap/android-studio/current/jbr   # or your Android Studio's jbr
./gradlew testDebugUnitTest   # JVM unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug        # install on a connected device or emulator
```

The first build downloads Gradle and the dependencies.

## Testing against a local DDEV site

The emulator can't resolve `*.ddev.site` to your computer, so forward the
device's port 80 to the host instead:

```
adb reverse tcp:80 tcp:80
```

Then enter `http://quicksketch.ddev.site` as the site address. `ddev.site`
resolves to 127.0.0.1, which `adb reverse` forwards to DDEV's router on your
computer.

Debug builds allow plain HTTP only to `*.ddev.site`, `10.0.2.2`, and
`localhost` (`app/src/debug/res/xml/network_security_config.xml`). Release
builds require HTTPS.

## Project layout

- `data/`: API models, the API client (`MobyApi`), request bodies, the site
  store with each site's encrypted token, and each site's settings.
- `util/`: HTML encoding of descriptions, and ISO 8601 dates.
- `ui/sites`, `ui/login`, `ui/feed`, `ui/post`, `ui/editor`, `ui/settings`: the screens and their view models.
