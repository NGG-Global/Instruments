# Flight Instruments

A native Android aircraft instrument panel written in Kotlin with Jetpack
Compose. It recreates — and substantially improves — the instrument panel from
the original `instruments.html`, which is kept in the repository purely as a
visual and behavioral reference. No WebView is used anywhere.

> **For supplemental / experimental use only. Not a certified flight
> instrument and not a substitute for approved aircraft instrumentation.**

## The five instruments

| Instrument | Data source | Fallback |
|---|---|---|
| ATTITUDE | `TYPE_GAME_ROTATION_VECTOR` (gyro/accel fusion) + SET LEVEL calibration | `TYPE_ROTATION_VECTOR` (marked DEGRADED) |
| HEADING | `TYPE_ROTATION_VECTOR` (magnetic-north referenced), tilt-compensated through the same mount calibration; TRUE heading adds `GeomagneticField` declination | `TYPE_GEOMAGNETIC_ROTATION_VECTOR`; GNSS track shown separately as TRK, never as heading |
| ALTIMETER | Barometer (`TYPE_PRESSURE`) + user QNH via the ISA barometric formula | GNSS MSL altitude (`AltitudeConverter`, API 34+) or WGS84 altitude, strongly filtered and labeled GNSS |
| VERTICAL SPEED | Filtered rate of change of barometric altitude | GNSS-altitude-derived rate with a long time constant, marked DEGRADED |
| GROUND SPEED | GNSS Doppler speed (`Location.speed`), converted m/s → knots | none — no fabricated movement when speed is unavailable |

Heading and GNSS track are modeled as **separate** values throughout. The
original HTML fed GPS track into the heading dial; that conceptual error is
deliberately not carried over.

## Architecture

```
sensor/      cold Flows over Android sensors (register on collect, unregister on cancel)
gnss/        LocationManager GPS provider only (offline), fix quality evaluator
flight/      FlightDataEngine + per-quantity estimators -> FlightState (StateFlow)
calibration/ SET LEVEL reference, mount orientation, QNH — DataStore persistence
recording/   raw-sample recorder + deterministic replay through the same pipeline
ui/          Compose rendering only; never touches raw sensors
math/        pure-Kotlin quaternions, angles, filters, barometric formula (JVM-tested)
```

Data flows in three layers: **RAW** sensor/GNSS samples → **ESTIMATED**
(time-constant filters in the flight layer) → **DISPLAY** (frame-time eased
needle animation in the UI). Filtering constants are documented at their
definition sites. Unavailable data is `null` plus an explicit
`DataQuality.UNAVAILABLE` — never zero.

### FlightState

One authoritative state object (`flight/FlightState.kt`) carries every value
the UI may show: pitch/roll, magnetic/true heading, track, selected/GNSS/baro
altitude, vertical speed, ground speed, GNSS accuracies, per-channel
`DataQuality`, and altitude/VSI source labels. The engine publishes it at
~30 Hz, decoupled from both sensor rates and display refresh.

### Attitude calibration (SET LEVEL)

The rotation vector gives the device→world (ENU) quaternion. At SET LEVEL the
reference orientation `q_ref` is captured and decomposed as
`q_ref = q_yaw ⊗ q_tilt` (swing–twist about world up). The mount correction is
`q_mount = q_tilt⁻¹ ⊗ Rz(mountYaw)`; the aircraft rotation at any time is
`q_cur ⊗ q_mount`, which is a pure yaw at the calibration instant. This is
exact rotation composition — not Euler subtraction — and absorbs any rigid
mounting tilt. The yaw offset (which device edge faces the nose) is
unobservable from a level reference, so it is an explicit setting
(`MountOrientation`). Calibration persists in DataStore.

### Rendering

The reference HTML regenerated per-pixel textures and thousands of noise
primitives every animation frame. The Compose implementation instead renders
each instrument's static hardware (housing, screws, bezel, dial face, scales,
labels) **once per size** into a cached bitmap inside `drawWithCache`;
per-frame work is only the moving parts: geometric horizon transforms
(clip + rotate + translate) for the attitude ball, one bitmap rotation for the
compass card, and needle paths. Needle values are read inside the draw phase,
so animation never recomposes the tree.

## Offline behavior

Everything works in airplane mode with Wi-Fi and mobile data off:

- No `INTERNET` permission (verified in the packaged APK).
- GNSS uses `LocationManager.GPS_PROVIDER` directly — no fused/network provider.
- MSL conversion uses the on-device `AltitudeConverter` geoid model (API 34+),
  guarded and falling back to WGS84 altitude on older releases.
- Declination comes from Android's bundled `GeomagneticField` model.
- All assets are drawn in code; all settings/calibration persist locally in
  DataStore; recordings stay in app-private storage.

## Calibration behavior

- **SET LEVEL** — onboarding step 4 or Settings; capture with the aircraft
  level and the device rigidly mounted. Recalibrate any time; not forced on
  every launch.
- **Mount orientation** — Settings; which device edge points to the nose.
- **QNH** — onboarding step 5, Settings, or tap the altimeter. Range
  850–1100 hPa, default 1013.25 hPa (standard pressure — the UI states that
  indicated altitude requires the local QNH).

## Recording and replay (diagnostics)

The diagnostics screen can record every raw sample (rotation vectors with
accuracy, pressure, full GNSS fixes with accuracy metadata, monotonic
timestamps) to a local text file, and replay it through the exact same
estimation pipeline. The engine's clock is sample-driven, so replays are
deterministic — filter changes can be evaluated without re-flying the same
movement. Recordings are never uploaded.

## Known hardware limitations

- No barometer → altitude and VSI fall back to GNSS: slower, noisier, marked
  DEGRADED; VSI uses a long time constant.
- No gyroscope / game rotation vector → attitude falls back to the magnetic
  rotation vector and is marked DEGRADED (magnetic disturbance sensitivity).
- No magnetometer / rotation vector → heading unavailable; only GNSS TRK shows.
- GNSS bearing requires motion (≥ ~3 kt); the track is blanked when stationary.
- Consumer sensors drift and are affected by vibration, temperature and
  magnetic interference — see the safety statement above.

## Build and run

Requirements: JDK 17+, Android SDK (compileSdk 35). `local.properties` must
point `sdk.dir` at the SDK.

```
./gradlew :app:assembleDebug        # build APK
./gradlew :app:testDebugUnitTest    # run unit tests
./gradlew :app:lintDebug            # static analysis
```

Install `app/build/outputs/apk/debug/app-debug.apk` on a device (minSdk 26).

## Tests

JVM unit tests cover unit conversions, angle normalization/wraparound/shortest
arc, quaternion algebra, the SET LEVEL calibration math (level, ±10° pitch,
±30° roll, combined, tilted and rotated mounts, wraparound headings), the
barometric formula and QNH shifts, the vertical-speed estimator (stationary
noise, constant climb/descent, outliers, data gaps), GNSS quality grading, the
ground-speed estimator, and the recording codec round-trip.
