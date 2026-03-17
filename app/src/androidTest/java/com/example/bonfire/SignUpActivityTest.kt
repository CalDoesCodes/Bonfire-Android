package com.example.bonfire

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignUpActivityTest {

    private lateinit var activity: SignUpActivity

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity = SignUpActivity()
        }
    }

    @Test
    fun testActivityLaunch() {
        ActivityScenario.launch(SignUpActivity::class.java).use {
            onView(withId(R.id.signup_username_edit)).check(matches(isDisplayed()))
            onView(withId(R.id.signup_email_edit)).check(matches(isDisplayed()))
            onView(withId(R.id.signup_password_edit)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testGetFriendlyErrorMessage() {
        val invalidEmail = FirebaseAuthInvalidCredentialsException("code", "message")
        assertEquals("Please enter a valid email address.", activity.getFriendlyErrorMessage(invalidEmail))

        val firebaseException = FirebaseException("message")
        assertEquals("Password does not meet requirements.", activity.getFriendlyErrorMessage(firebaseException))

        val otherException = Exception()
        assertEquals("An unexpected error occurred. Please try again.", activity.getFriendlyErrorMessage(otherException))
    }

    @Test
    fun testSignUp_EmptyFields() {
        ActivityScenario.launch(SignUpActivity::class.java).use {
            onView(withId(R.id.signup_button)).perform(click())
            // Logic check: should not proceed to Firebase.
        }
    }


}
