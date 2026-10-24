package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.FloatingBallPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Floating Ball", appName)
  }

  @Test
  fun `preferences store and retrieve target app and configurations`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = FloatingBallPreferences.getInstance(context)

    prefs.setTargetApp("com.android.chrome", "Google Chrome")
    val settings = prefs.getSettings()

    assertEquals("com.android.chrome", settings.targetPackage)
    assertEquals("Google Chrome", settings.targetAppName)

    prefs.setBallSize(64)
    assertEquals(64, prefs.getSettings().ballSizeDp)

    prefs.setAutoHideOnLaunch(true)
    assertTrue(prefs.getSettings().autoHideOnLaunch)

    prefs.setSnapToEdge(true)
    assertTrue(prefs.getSettings().snapToEdge)

    prefs.setAutoCleanActiveApp(true)
    assertTrue(prefs.getSettings().autoCleanActiveApp)

    // Verify recents dismiss target defaults to "right" (user requested right card removal)
    assertEquals("right", prefs.getSettings().recentsDismissTarget)

    prefs.setRecentsDismissTarget("center")
    assertEquals("center", prefs.getSettings().recentsDismissTarget)

    prefs.setRecentsDismissTarget("both")
    assertEquals("both", prefs.getSettings().recentsDismissTarget)

    prefs.setRecentsDismissTarget("right")
    assertEquals("right", prefs.getSettings().recentsDismissTarget)

    prefs.setTargetApp("com.google.android.apps.tachyon", "Google Meet (Call Switcher)", isMeet = true)
    assertTrue(prefs.getSettings().isMeetMode)
    assertEquals("com.google.android.apps.tachyon", prefs.getSettings().targetPackage)
  }
}
