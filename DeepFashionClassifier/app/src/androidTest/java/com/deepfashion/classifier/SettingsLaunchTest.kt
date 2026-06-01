package com.deepfashion.classifier

import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsLaunchTest {

    @Before
    fun disableOnboarding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(MainContainerActivity.PREF_ONBOARDING_DONE, true)
            .apply()
    }

    @Test
    fun settingsActivityOpensFromMoreTab() {
        ActivityScenario.launch(MainContainerActivity::class.java).use {
            onView(withId(R.id.nav_more)).perform(click())
            onView(withId(R.id.cardSettings)).perform(click())
            onView(withId(android.R.id.content)).check(matches(isDisplayed()))
        }
    }
}
