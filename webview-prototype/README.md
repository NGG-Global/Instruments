> ## Status: standalone prototype — not part of the Instruments app
>
> This directory holds a **separate, self-contained Android project**
> (`com.example.instrumentsandnav`) that renders its instrument panel by loading
> `app/src/main/assets/instruments_nav.html` into a WebView. It is kept here for
> reference and for building test APKs.
>
> It is **not** the app this repository ships. The repository's product is the
> native Kotlin/Compose panel under `app/` (`com.ngg.instruments`), which uses no
> WebView — see `AGENTS.md`. The two builds are independent: different
> application IDs, different Gradle builds, different source. Nothing in the root
> project depends on anything here, and the architecture rules in `AGENTS.md`
> apply to the native app, not to this prototype.
>
> Being HTML-driven, this prototype does not observe the native app's data
> guarantees — most notably it drives the heading dial from GNSS track, which the
> native app deliberately treats as a separate quantity. Do not use it as a
> behavioral reference.

## Building an APK

Requires JDK 17+ and an Android SDK with platform 36 and build-tools 36.0.0.
Point the build at the SDK, then assemble:

```sh
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease    # unsigned; sign with apksigner before use
```

`./gradlew` here is not a standard Gradle wrapper — it is a shell script that
downloads Gradle 9.5.0 into `.gradle-dist/` on first run, so the first build
needs network access. The release output is unsigned; align and sign it with a
keystore you control:

```sh
zipalign -p -f 4 app-release-unsigned.apk app-release.apk
apksigner sign --ks release.jks --ks-key-alias <alias> app-release.apk
apksigner verify --print-certs app-release.apk
```

Release APKs are signed out of band; no keystore is committed to this repository.

---

# Instruments and nav

Android test project for the OnePlus 7T cockpit instrument panel and navigation window.

## Online route retrieval — stage 1

- Enter a 3-letter IATA or 4-letter ICAO origin and destination.
- Android resolves the airports online through Flight Plan Database.
- It searches stored flight plans for that airport pair, downloads the selected plan, and sends the route nodes (identifier + latitude/longitude) to the NAV display.
- The NAV list calculates straight-line leg mileage between the returned route nodes.
- If online lookup fails, the existing PVK-ATH and SEA-TWF local test routes remain available as fallback.
- This stage does not require an API key.
- A later stage can use the authenticated Flight Plan Database /auto/generate endpoint to generate a route when no stored plan exists.

Flight Plan Database data is for flight simulation use only and is not suitable for real-world navigation.

## Online route lookup stage 2
- Fixes 3-letter IATA -> ICAO resolution. Flight Plan Database's navaid search is not an IATA lookup, so stage 1 could fail on entries such as LHR and PVK.
- IATA codes are now resolved with Airport-Data.com, then the resulting ICAO pair is used for Flight Plan Database route search.
- Recommended test: LHR -> PVK, which should resolve to EGLL -> LGPZ and display the stored route if the services are reachable.
- Note: PKV is a different IATA code from PVK. The app will resolve the code exactly as entered; it will not silently autocorrect airport codes.

## NAV heading line update
- Each waypoint row now shows heading/bearing in degrees and leg distance in miles.
- The active waypoint is highlighted; tap another waypoint row to make it active.
- With a GPS fix, the active row heading becomes the live true bearing from the phone position to that waypoint.
- The heading instrument remains GPS-track-only. A thin green line from the dial center shows the active waypoint bearing relative to current GPS track.


## GPS status / reset view
- Top GPS label is green while GPS fixes are fresh and red after about 6 seconds without a GPS fix or when the GPS provider is disabled.
- RESET VIEW restores the floating attitude and NAV windows to their default position and size.

## Preloaded flight plan behavior
The last successfully retrieved complete flight plan is saved locally on the phone. On the next app launch it is restored immediately into the NAV window, including origin/destination, waypoints, distances and headings, without requiring Internet access at startup. Entering/changing origin or destination performs a new online lookup; after a successful lookup that new plan becomes the preloaded plan for subsequent launches.
