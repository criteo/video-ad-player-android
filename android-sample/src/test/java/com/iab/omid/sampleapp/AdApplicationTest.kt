package com.iab.omid.sampleapp

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.iab.omid.sampleapp.manager.omid.OMIDSessionInteractorFactory
import com.iab.omid.sampleapp.manager.omid.OMIDSessionInteractorStub
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.view.View

@RunWith(RobolectricTestRunner::class)
class AdApplicationTest {

    @Test
    fun `GIVEN manifest WHEN application starts THEN it is an AdApplication`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(app is AdApplication)
    }

    @Test
    fun `GIVEN application created WHEN onCreate runs again THEN it does not throw`() {
        val app = ApplicationProvider.getApplicationContext<Application>() as AdApplication
        app.onCreate()
    }

    @Test
    fun `GIVEN OMID SDK not bundled WHEN activating THEN factory reports false`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertFalse(OMIDSessionInteractorFactory.activateOMSDK(app))
    }

    @Test
    fun `GIVEN OMID SDK not bundled WHEN creating interactor THEN a stub is returned`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val interactor = OMIDSessionInteractorFactory.create(app, View(app), "v", "u", "p")
        assertTrue(interactor is OMIDSessionInteractorStub)
    }
}
