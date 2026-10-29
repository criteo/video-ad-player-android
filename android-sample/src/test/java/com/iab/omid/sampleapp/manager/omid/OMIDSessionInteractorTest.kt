package com.iab.omid.sampleapp.manager.omid

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OMIDSessionInteractorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `GIVEN stub WHEN invoking every interactor method THEN none throws`() {
        val stub: IOMIDSessionInteractor = OMIDSessionInteractorStub()
        stub.startSession()
        stub.fireAdLoaded()
        stub.fireImpression()
        stub.fireStart(10_000L, 1f)
        stub.fireFirstQuartile()
        stub.fireMidpoint()
        stub.fireThirdQuartile()
        stub.fireComplete()
        stub.firePause()
        stub.fireResume()
        stub.fireVolumeChange(0f)
        stub.fireBufferStart()
        stub.fireBufferFinish()
        stub.fireSkipped()
        stub.fireClickInteraction()
        stub.addMediaControlsObstruction(View(context))
        stub.stopSession()
    }

    @Test
    fun `GIVEN factory WHEN creating interactor THEN stub is returned`() {
        val interactor = OMIDSessionInteractorFactory.create(context, View(context), "vendor", "https://example.com/s.js", "{}")
        assertTrue(interactor is OMIDSessionInteractorStub)
    }

    @Test
    fun `GIVEN factory WHEN activating OMSDK THEN false because SDK is not bundled`() {
        assertFalse(OMIDSessionInteractorFactory.activateOMSDK(context))
    }
}
