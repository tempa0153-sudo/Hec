package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Movies & Series", appName)
  }

  @Test
  fun `verify sticky mute state defaults to false (sound on)`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = com.example.data.pref.UserPreferences(context)
    assertEquals(false, prefs.userProfile.value.trailerMuted)
    prefs.updateTrailerMuted(true)
    assertEquals(true, prefs.userProfile.value.trailerMuted)
    prefs.updateTrailerMuted(false)
    assertEquals(false, prefs.userProfile.value.trailerMuted)
  }
}
