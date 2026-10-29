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

A single scenario method: append `#methodName` to the class, for example `...scenario.AppSwitchingScenarioTest#product_34910_manually_paused_video_stays_paused_after_app_switch`.

Android Studio can also run any test file or method with the green gutter icon.

### Regression tests

Regression tests are named after the ticket they protect. Currently:

| Ticket | Behaviour protected | Tests |
|---|---|---|
| PRODUCT-34910 | A video paused manually must stay paused after the app goes to the background and returns. A playing video must still auto-resume. | `Product34910RegressionTest` (unit) and `AppSwitchingScenarioTest.product_34910_manually_paused_video_stays_paused_after_app_switch` (scenario) |

```bash
sh ./gradlew testDefaultsDebugUnitTest --tests '*Product34910RegressionTest'
```

When you fix a bug, add a test that fails without your fix and name it after the ticket.

## What the scenario tests cover

| Class | Behaviour |
|---|---|
| `BasicPlayerScenarioTest` | Muted autoplay, `impression`/`start` beacons, no `mute` beacon at launch, mute/unmute, pause/resume buttons, tapping the video when there is no redirect link |
| `ClickThroughScenarioTest` | Redirect link opens the landing page and fires `clickTracking` on every tap, never `pause`; no link means no page opens |
| `QuartileScenarioTest` | `start`, `firstQuartile`, `midpoint`, `thirdQuartile`, `complete` fire once each, in order, and the video keeps looping |
| `ClosedCaptionAndMediaScenarioTest` | CC button shown and enabled only when a caption file exists; media file selection by aspect ratio |
| `AppSwitchingScenarioTest` | Pause on background and resume on return without extra beacons, mute state and position preserved, PRODUCT-34910 |
| `FeedViewportScenarioTest` | Viewport-driven load, autoplay, and auto pause/resume (see the skipped tests below) |

### Skipped tests

Five tests in `FeedViewportScenarioTest` are marked `@Ignore`, each with the reason in the annotation:

- Four depend on a known issue: the video does not autoplay when loading finishes while the ad is already at least 50% visible, so no `start` beacon fires until the ad is scrolled out and back.
- One covers the "load at page load, even off-screen" rule. The Feed screen only creates the player when the ad row is first bound.

When the underlying bug is fixed, remove the `@Ignore` line so the test starts guarding it.

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
        assertTrue(server.awaitBeacon("unmute"))
    }
}
```

- `VastSpec` controls the fake VAST: media renditions, click-through URL, captions, or a broken response.
- `server.awaitBeacon(name)` waits for a beacon; `server.neverFires(name)` asserts it stays unfired; `server.count(name)` and `server.names()` inspect what arrived.
- `PlayerRobot` finds the player views and taps play/pause, mute, or the video surface like a user.
- Always call `awaitPlaying` before interacting. The player only fires resume beacons once playback has passed the first quartile, so tapping earlier makes tests flaky.
- Prefer waiting on a condition (`waitUntil`, `awaitBeacon`) over fixed sleeps.

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
