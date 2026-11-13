package com.iab.omid.sampleapp.scenario

/** VAST event names shared by the fake server and scenario assertions. */
enum class BeaconEvent(val xmlName: String) {
    IMPRESSION("impression"),
    START("start"),
    FIRST_QUARTILE("firstQuartile"),
    MIDPOINT("midpoint"),
    THIRD_QUARTILE("thirdQuartile"),
    COMPLETE("complete"),
    MUTE("mute"),
    UNMUTE("unmute"),
    PAUSE("pause"),
    RESUME("resume"),
    CLICK_TRACKING("clickTracking")
}
