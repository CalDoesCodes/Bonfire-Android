package com.example.bonfire

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignInActivityTest {


    @Test
    fun testSignIn_EmptyFields() {
        ActivityScenario.launch(SignInActivity::class.java).use {
            onView(withId(R.id.signin_button)).perform(click())
            // Verifying no crash. Toast is harder to test without custom matchers.
        }
    }

    @Test
    fun testSwitchToSignUp() {
        ActivityScenario.launch(SignInActivity::class.java).use {
            onView(withId(R.id.signin_switch_button)).perform(click())
            // Should finish or navigate to SignUpActivity
        }
    }
}
