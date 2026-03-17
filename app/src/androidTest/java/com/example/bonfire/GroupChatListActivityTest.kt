package com.example.bonfire

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupChatListActivityTest {

    private lateinit var activity: GroupChatListActivity

    @Before
    fun setUp() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            activity = GroupChatListActivity()
        }
    }

    @Test
    fun testGetChatIdWithFriend() {
        val friendId = "friend123"
        val chatId = activity.getChatIdWithFriend(friendId)
        assertTrue("Chat ID should start with chats/", chatId.startsWith("chats/"))
        assertTrue("Chat ID should end with /messages", chatId.endsWith("/messages"))
    }

    @Test
    fun testActivityLaunch() {
        ActivityScenario.launch(GroupChatListActivity::class.java).use {
            onView(withId(R.id.list_content)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testNavButtons() {
        ActivityScenario.launch(GroupChatListActivity::class.java).use {
            onView(withId(R.id.menu_button_friends)).check(matches(isDisplayed()))
            onView(withId(R.id.menu_button_account)).check(matches(isDisplayed()))
            onView(withId(R.id.menu_button_logout)).check(matches(isDisplayed()))
        }
    }
}
