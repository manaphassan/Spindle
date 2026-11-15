package com.hana.spindle

import android.Manifest
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.hana.spindle.ui.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests that boot the real app on a device. Unit tests can't see startup wiring between
 * SpindleApp, AudioEngine and AudioMetricsTracker; a recursion there once crashed every launch.
 */
@RunWith(AndroidJUnit4::class)
class StartupSmokeTest {

    // Pre-grant what MainActivity asks for, so no permission dialog pauses it mid-launch.
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(*runtimePermissions())

    @Test
    fun audioEngineInitialisesOnMainThread() {
        val app = ApplicationProvider.getApplicationContext<SpindleApp>()
        var tracker: Any? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            tracker = app.audioEngine.metricsTracker
        }
        // The tracker's first telemetry update is posted; let it run too.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertNotNull(tracker)
    }

    @Test
    fun mainActivityLaunchesAndResumes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertNotNull(activity.window.decorView)
                assertFalse("MainActivity finished during startup", activity.isFinishing)
            }
        }
    }

    private companion object {
        fun runtimePermissions(): Array<String> = buildList {
            add(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
        }.toTypedArray()
    }
}
