# Contributing

Thanks for helping improve the Criteo Video Ad Player for Android. This guide covers the project layout, how to run the tests (all of them, or just the ones you care about), and how to add new ones.

## Project layout

Everything lives in the `android-sample` Gradle module. Run all Gradle commands from that folder.

```
android-sample/
  src/main/                   App + player code (package com.iab.omid.sampleapp)
    player/                   CriteoVideoPlayer, CriteoVideoAdWrapper, tracking/Quartile
    manager/                  BeaconManager, NetworkManager, CreativeDownloader, vast/, omid/
    util/                     CriteoLogger
  src/test/                   Unit tests (JVM, no device needed)
  src/androidTest/            Scenario tests (need an emulator or a device)
    assets/                   sample.mp4 and sample.vtt used as test fixtures
    .../scenario/             ScenarioServer, PlayerRobot, ScenarioBase and the *ScenarioTest classes
```

## Prerequisites

- JDK and the Android SDK (Android Studio installs both).
- The Gradle wrapper is checked in. If `./gradlew` gives "permission denied", run it through the shell instead: `sh ./gradlew ...` (the examples below do this).
- The product flavor is `defaults`, so Gradle task names contain `DefaultsDebug`.

## Running the tests

### Unit tests (no emulator, about 1 minute)

```bash
sh ./gradlew testDefaultsDebugUnitTest
```

Report: `build/reports/tests/testDefaultsDebugUnitTest/index.html`

They use JUnit 4, Robolectric (Android classes on the JVM), MockK, MockWebServer and `kotlinx-coroutines-test`.

### Scenario tests (emulator or device required, about 3 minutes)

These run the real Basic and Feed screens against a local fake ad server and assert on the beacons the player fires.

1. Start an emulator (Android Studio > Device Manager) or plug in a phone with USB debugging.
2. Check it is visible:
   ```bash
   adb devices
   ```
3. Run:
   ```bash
   sh ./gradlew connectedDefaultsDebugAndroidTest
   ```

Report: `build/reports/androidTests/connected/`

Notes:

- With no connected device Gradle fails with "No connected devices!". That is expected; run only the unit tests in that case.
- No internet is needed. The test server and the sample video are local.
- They were developed and run on a Pixel 8 emulator (API 37). Other API levels from the app's `minSdk` (24) up should work, but have not all been tried.

### Running specific tests

Single unit test class (use `--tests` more than once to run several):

```bash
sh ./gradlew testDefaultsDebugUnitTest --tests '*BeaconManagerTest'
sh ./gradlew testDefaultsDebugUnitTest --tests 'com.iab.omid.sampleapp.player.tracking.QuartileTest'
```

Test method names contain spaces, so it is easier to run a class and then open the HTML report, or to run a single method from Android Studio.

Single scenario test class (comma-separate several classes):

```bash
sh ./gradlew connectedDefaultsDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.iab.omid.sampleapp.scenario.BasicPlayerScenarioTest
```

A single scenario method: append `#methodName` to the class, for example `...scenario.AppSwitchingScenarioTest#manually_paused_video_stays_paused_after_app_switch`.

Android Studio can also run any test file or method with the green gutter icon.

### Regression tests

Name regression tests after the behaviour they protect; keep ticket references in comments for context. Currently:

| Ticket | Behaviour protected | Tests |
|---|---|---|
| PRODUCT-34910 | A video paused manually must stay paused after the app goes to the background and returns. A playing video must still auto-resume. | `PlaybackStateRegressionTest` (unit) and `AppSwitchingScenarioTest.manually_paused_video_stays_paused_after_app_switch` (scenario) |

```bash
sh ./gradlew testDefaultsDebugUnitTest --tests '*PlaybackStateRegressionTest'
```

When you fix a bug, add a test that fails without your fix. Use a descriptive behaviour name and include the ticket in a comment.

## What the scenario tests cover

| Class | Behaviour |
|---|---|
| `BasicPlayerScenarioTest` | Muted autoplay, `impression`/`start` beacons, no `mute` beacon at launch, mute/unmute, pause/resume buttons, tapping the video when there is no click-through URL |
| `ClickThroughScenarioTest` | Click-through URL opens the landing page and fires `clickTracking` on every tap, never `pause`; no link means no page opens |
| `QuartileScenarioTest` | `start`, `firstQuartile`, `midpoint`, `thirdQuartile`, `complete` fire once each, in order, and the video keeps looping |
| `ClosedCaptionAndMediaScenarioTest` | CC button shown and enabled only when a caption file exists; media file selection by aspect ratio |
| `AppSwitchingScenarioTest` | Pause on background and resume on return without extra beacons, mute state and position preserved, PRODUCT-34910 |
| `FeedViewportScenarioTest` | Viewport-driven load, autoplay, and auto pause/resume (see the skipped tests below) |

### Skipped tests

Five tests in `FeedViewportScenarioTest` are marked `@Ignore`, each with the reason in the annotation:

- Three are blocked by a **known bug in initial autoplay**: `onVisibilityChanged(true)` can call `play()` before `videoPlayer` exists. The wrapper then loads with `autoLoad = false`, and the holder's visibility flag prevents another play attempt while the ad stays visible. These tests cover initial autoplay and the two pause/resume flows that depend on it.
- One is **test under implementation**: the diagnostic that waits for loading, scrolls away and returns. Its earlier failure has not been isolated to app code; verify player readiness and RecyclerView visibility transitions before attributing it to a bug.
- One covers a **known bug in off-screen preloading**: the Feed screen only creates the player when the ad row is first bound, so it does not fetch an off-screen ad at page load.

The annotations distinguish confirmed app bugs from unfinished test diagnostics. Re-enable each test once its particular cause is addressed; fixing initial autoplay alone does not resolve every skipped test.

### Not covered

The OMID beacons (`omidTrackView`, `twoSecondsFiftyPercentView`, and so on). `OMIDSessionInteractorFactory` always returns the no-op `OMIDSessionInteractorStub`, so there is nothing to observe.

## Writing scenario tests

Extend `ScenarioBase`. It starts a local `ScenarioServer` and launches `MainActivity` directly on the Basic or Feed screen.

```kotlin
@RunWith(AndroidJUnit4::class)
class MyScenarioTest : ScenarioBase() {
    @Test
    fun my_behaviour() {
        val activity = launchBasic(VastSpec(clickThrough = "https://example.com/landing"))
        awaitPlaying(activity)              // waits for playback and the `start` beacon
        PlayerRobot.tapMute(activity)
        assertTrue("user unmute must fire a beacon", server.hasReceivedBeacon(BeaconEvent.UNMUTE))
    }
}
```

- `VastSpec` controls the fake VAST: media renditions, click-through URL, click-tracking URLs, captions, or a broken response. `clickThrough` and `clickTrackingUrls` are independent: use `clickTrackingUrls = emptyList()` to omit tracking, or configure tracking without a click-through URL to check that it stays unfired. Relative tracking URLs resolve against the local server.
- Pass a `BeaconEvent` enum value to `server.hasReceivedBeacon(event)` to wait for a beacon, `server.neverFires(event)` to observe its absence, or `server.countReceivedBeacon(event)` to count requests. `server.names()` returns the received events in order. `ScenarioTimeouts` defines shared deadlines and observation windows.
- `PlayerRobot` finds the player views, taps play/pause, mute, or the video surface, reads caption state on the main thread, and scrolls the Feed with `scrollFeedTo(position)`. Put shared UI actions here and shared test assertions in `ScenarioBase`.
- Always call `awaitPlaying` before interacting. This waits for playback and the `start` beacon (at 1% progress); interacting before that point can change which beacon is emitted.
- Give assertions a message describing the expected behaviour.
- Prefer waiting on a condition (`waitUntil`, `hasReceivedBeacon`) over fixed sleeps.

## Writing unit tests

- Use Robolectric (`@RunWith(RobolectricTestRunner::class)`) for anything that touches Android classes, and plain JUnit for pure logic. The Robolectric SDK is set in `src/test/resources/robolectric.properties`.
- Name tests in the existing `GIVEN ... WHEN ... THEN ...` style.
- Don't hit the real network. Use MockWebServer or an unreachable local URL.

## Checking coverage locally (optional)

Coverage tooling is deliberately not part of the build, to keep this public repo lean. If you want a local report, put a small Gradle init script outside the repo that turns on unit test coverage for the debug build, and run it with `-I`:

```bash
sh ./gradlew -I /path/to/coverage.init.gradle createDefaultsDebugUnitTestCoverageReport
```

Because the Robolectric tests load classes in their own classloader, the script must also set `includeNoLocationClasses = true` on the JaCoCo test task extension, otherwise the player classes show 0% coverage. The report is written to `build/reports/coverage/test/defaults/debug/`.

## Before opening a pull request

- Run the unit tests. Run the scenario tests too if you touched the player, wrapper, beacons, or the Basic/Feed screens.
- Add or update tests for your change, plus a regression test if it fixes a bug.
- Don't commit generated folders (`build`, `.gradle`, `.kotlin`, `.idea`) or `local.properties`.
