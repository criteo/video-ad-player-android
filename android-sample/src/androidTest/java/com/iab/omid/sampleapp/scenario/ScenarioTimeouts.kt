package com.iab.omid.sampleapp.scenario

/** Upper bounds for asynchronous work; observation windows also catch events that must not occur. */
object ScenarioTimeouts {
    const val POLL_INTERVAL_MS = 100L
    const val STATE_CHANGE_MS = 5_000L
    const val PLAYBACK_START_MS = 30_000L
    const val ASSET_LOAD_MS = 15_000L
    const val APP_SWITCH_MS = 15_000L
    const val BEACON_MS = 20_000L
    const val NO_BEACON_MS = 2_000L
    const val OBSERVATION_MS = 3_000L
    const val VIDEO_COMPLETE_MS = 120_000L
}
