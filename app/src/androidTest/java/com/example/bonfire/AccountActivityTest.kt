package com.example.bonfire

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountActivityTest {
    @Test
    fun testActivityLaunch() {
        ActivityScenario.launch(AccountActivity::class.java).use {
            onView(withId(R.id.account_name)).check(matches(isDisplayed()))
            onView(withId(R.id.account_email)).check(matches(isDisplayed()))
            onView(withId(R.id.account_avatar)).check(matches(isDisplayed()))
            onView(withId(R.id.account_grid)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testNavButtons() {
        ActivityScenario.launch(AccountActivity::class.java).use {
            onView(withId(R.id.menu_button_chat)).check(matches(isDisplayed()))
            onView(withId(R.id.menu_button_friends)).check(matches(isDisplayed()))
            onView(withId(R.id.menu_button_logout)).check(matches(isDisplayed()))
        }
    }
    
    @Test
    fun testAvatarClick() {
        ActivityScenario.launch(AccountActivity::class.java).use {
            // Match a specific icon by ID to avoid ambiguity
            onView(withId(R.id.icon1)).perform(click())
        }
    }
}
