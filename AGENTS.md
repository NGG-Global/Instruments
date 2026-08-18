# Repository guidance

Native Android app (Kotlin + Jetpack Compose) implementing an aircraft
instrument panel. `instruments.html` is a frozen visual reference only — do
not extend it, do not wrap it in a WebView.

`webview-prototype/` is a separate, self-contained project kept for reference
and test APKs. It is not this app, nothing here depends on it, and the rules
below do not describe it. Leave it alone unless a task names it explicitly.

## Commands

```
./gradlew :app:assembleDebug        # build
./gradlew :app:testDebugUnitTest    # unit tests (all must pass)
./gradlew :app:lintDebug            # lint (must stay clean)
```

`local.properties` needs `sdk.dir` pointing at an Android SDK with platform 35.

## Architecture boundaries (do not blur these)

- `sensor/` and `gnss/` produce raw `RawSample`s only. No filtering there.
- `flight/` owns all estimation/filtering and publishes `FlightState`.
- `ui/` renders `FlightState`. **The UI never processes raw sensor values.**
  The only UI-side math is display easing (`ui/DisplayValue.kt`).
- `math/` is pure Kotlin (no Android imports) so it stays JVM-testable.
- Recording/replay feed the engine through the same `FlightDataSources`
  interface as live sensors; keep replay deterministic (engine time is
  sample-driven, never wall-clock).

## Hard rules

- **GNSS track must never be relabeled heading.** `trackDeg` (course over
  ground) and `magneticHeadingDeg`/`trueHeadingDeg` (nose direction) are
  separate `FlightState` fields; the heading dial shows HDG, track only as a
  secondary TRK readout.
- **Unavailable data must never silently become zero.** Use nullable fields
  plus `DataQuality.UNAVAILABLE`. Instruments hide needles / show flags.
- **No INTERNET permission**, no network SDKs, no remote assets. Verify with
  `aapt2 dump permissions` after manifest changes.
- Attitude calibration is quaternion composition (`AttitudeMath`), never Euler
  subtraction. Changes there require the `AttitudeMathTest` suite to pass.
- Filters take elapsed time (time constants), never per-frame factors.
- Static instrument artwork renders once per size into cached bitmaps;
  per-frame drawing is transforms and needles only. No per-pixel loops.

## Sensor assumptions

- Rotation vectors are device→world (ENU: X east, Y north, Z up) quaternions;
  `TYPE_GAME_ROTATION_VECTOR` has arbitrary yaw, `TYPE_ROTATION_VECTOR` is
  magnetic-north referenced.
- Aircraft body frame after mount correction: X right wing, Y forward, Z up;
  positive pitch = nose up, positive roll = right bank.
- Barometric altitude uses the ISA formula 44330·(1−(p/QNH)^(1/5.255)) — the
  same formula as `SensorManager.getAltitude`, re-implemented for testability.
- GNSS altitude is WGS84 unless converted; MSL conversion only via
  `AltitudeConverter` behind an API 34 guard that must never crash older
  devices.
- Timestamps are `elapsedRealtimeNanos`-domain monotonic values end to end.
