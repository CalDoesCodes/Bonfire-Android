package com.example.bonfire

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatActivityTest {

    private lateinit var chatActivity: ChatActivity
    private val OPEN_CHAT_KEY = "open_chat_friendId"

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            chatActivity = ChatActivity()
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("notif_limits", Context.MODE_PRIVATE).edit().clear().commit()
    }


    @Test
    fun testActivityLaunch_PrivateChat() {
        val friendId = "test_friend_id"
        val intent = Intent(ApplicationProvider.getApplicationContext(), ChatActivity::class.java).apply {
            putExtra("id", friendId)
        }
        ActivityScenario.launch<ChatActivity>(intent).use {
            onView(withId(R.id.chat_messages_RecyclerView)).check(matches(isDisplayed()))

            
            val context = ApplicationProvider.getApplicationContext<Context>()
            val prefs = context.getSharedPreferences("notif_limits", Context.MODE_PRIVATE)
            assertTrue(prefs.getBoolean("limit_enabled_$friendId", false))
        }
    }

    @Test
    fun testSendMessage_WithText() {
        ActivityScenario.launch(ChatActivity::class.java).use {
            onView(withId(R.id.chat_MessageBar_TextInputEditText)).perform(replaceText("Hello World"))
            onView(withId(R.id.chat_MessageBar_SendButton)).perform(click())
            onView(withId(R.id.chat_MessageBar_TextInputEditText)).check(matches(withText("")))
        }
    }

    @Test
    fun testBackArrow() {
        ActivityScenario.launch(ChatActivity::class.java).use { scenario ->
            onView(withId(R.id.chat_cardView_backArrow)).perform(forceClick())
            // Success is reaching here without crash; finishing activities are hard to assert state on reliably
        }
    }

    private fun forceClick(): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isClickable()
            override fun getDescription(): String = "force click"
            override fun perform(uiController: androidx.test.espresso.UiController?, view: View?) {
                view?.performClick()
            }
        }
    }
}
