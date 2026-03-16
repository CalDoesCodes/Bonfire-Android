package com.example.bonfire

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import androidx.core.content.edit


class GroupChatListActivity : AppCompatActivity() {
    val TAG = "chat list"
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val db = Firebase.firestore
    val helper = Helper()
    private fun notifPrefs() = getSharedPreferences("notif_limits", MODE_PRIVATE)
    private fun limitEnabledKey(friendId: String) = "limit_enabled_$friendId"
    private fun unopenedKey(friendId: String) = "unopened_$friendId"




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.groupchat_list_layout)

        if (!uid.isNullOrEmpty()) {
            var userData : Map<String, Object>
            val userRef = db.collection("users").document(uid ?: "")
            userRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    userData = document.data as Map<String, Object>
                    val userFriends = userData["friends"]
                    Log.d(TAG, "user friend list found")
                    if (userFriends != null){
                        populateFriendList(db, userFriends as List<String>)
                    } else{
                        // Add text if user has no friends
                        val groupChatList : LinearLayout = findViewById(R.id.list_messages_LinearLayout)
                        val noFriendText = TextView(this)
                        noFriendText.text = "You have no friends. Send a request!"
                        noFriendText.setPadding(24, 24, 24, 24)
                        noFriendText.textSize = 20.toFloat()
                        noFriendText.textAlignment = View.TEXT_ALIGNMENT_CENTER
                        groupChatList.addView(noFriendText)

                        // delete loading icon
                        val loading : TextView = findViewById(R.id.groupchat_list_loading)
                        (loading.parent as ViewManager).removeView(loading)
                    }
                    Log.d(TAG, "${userFriends.toString()} user friend list found")
                } else {
                    Log.d(TAG, "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception)
            }
        } else {
             // Handle guest or unauthenticated mode for tests/etc
             val loading : TextView = findViewById(R.id.groupchat_list_loading)
             (loading.parent as? ViewManager)?.removeView(loading)
        }

        // Open global chat button
        val globalChat = findViewById<View>(R.id.global_chat)
        val openChatButton = globalChat.findViewById<CardView>(R.id.card_chat_list_message)
        openChatButton.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            startActivity(intent)
            finish()
        }

        helper.listenForNotifs(uid ?: "", this)

        defineBottomNavButtons()
    }

    // Generate list of friends, with a button that will open the specific private message message with them
    private fun populateFriendList(db: FirebaseFirestore, userFriends:List<String>) {
        val groupChatList : LinearLayout = findViewById(R.id.list_messages_LinearLayout)
        val blockedPref = getSharedPreferences("blocked", MODE_PRIVATE)

        for (friendId in userFriends) {
            // Skip if blocked
            if (blockedPref.getBoolean(friendId, false)) continue

            Log.d(TAG, "friendId $friendId")

            // Find data of friend
            val docRef = db.collection("users").document(friendId)
            docRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val friendData = document.data
                    // Friend data found
                    // dynamically generate friend view in list
                    val friendView = LayoutInflater.from(this).inflate(R.layout.groupchat_layout, null, false)

                    val friendName : TextView = friendView.findViewById(R.id.text_chat_list_user)
                    friendName.text = (friendData?.get("name") ?: "") as String

                    val friendAvatarView : ShapeableImageView = friendView.findViewById(R.id.text_chat_list_avatar)
                    val avatar = (friendData?.get("avatar") ?: "") as String
                    helper.setProfilePicture(this, avatar, friendAvatarView)


                    // Generate button listener that will open chat with friend
                    val openChatButton = friendView.findViewById<CardView>(R.id.card_chat_list_message)
                    openChatButton.setOnClickListener {
                        val intent = Intent(this, ChatActivity::class.java)

                        // Passes friendID to chat activity
                        intent.putExtra("id", friendId)
                        ContextCompat.startActivity(this, intent, null)
                    }

                    // button listener to show dropdown menu for options
                    val optionsButton: ImageButton = friendView.findViewById(R.id.text_chat_list_message_options)
                    optionsButton.setOnClickListener { view ->
                        val popup = PopupMenu(this, view)
                        popup.menuInflater.inflate(R.menu.chat_options_menu, popup.menu)

                        // Update Mute menu item text based on current state
                        val sharedPref = this.getSharedPreferences("muted", MODE_PRIVATE)
                        val isFriendMuted : Int = sharedPref.getInt(friendId, 0)
                        val muteItem = popup.menu.findItem(R.id.action_mute)
                        muteItem.title = if (isFriendMuted == 1) "Unmute" else "Mute"

                        // same for if limited notifications
                        val isFriendLimited = notifPrefs().getBoolean(limitEnabledKey(friendId), false)
                        val limitItem = popup.menu.findItem(R.id.action_limit)
                        limitItem.title = if (isFriendLimited) "Unlimit notifications" else "Limit notifications"

                        popup.setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_mute -> {
                                    // toggle if friend is muted, 0 <-> 1 / false <-> true
                                    val newMuteStatus = if (isFriendMuted == 1) 0 else 1
                                    sharedPref.edit {
                                        putInt(friendId, newMuteStatus)
                                    }

                                    if (newMuteStatus == 0) {
                                        Toast.makeText(baseContext, "${friendName.text} has been unmuted.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(baseContext, "${friendName.text} has been muted.", Toast.LENGTH_SHORT).show()
                                    }
                                    true
                                }
                                R.id.action_block -> {
                                    blockedPref.edit {
                                        putBoolean(friendId, true)
                                        putString("name_$friendId", friendName.text.toString())
                                    }
                                    // Also mute the user when blocking
                                    sharedPref.edit {
                                        putInt(friendId, 1)
                                    }
                                    groupChatList.removeView(friendView)
                                    Toast.makeText(baseContext, "Blocked ${friendName.text}", Toast.LENGTH_SHORT).show()
                                    true
                                }
                                R.id.action_limit -> {
                                    // Save changes when user toggles
                                    notifPrefs().edit{
                                        putBoolean(limitEnabledKey(friendId), !isFriendLimited)
                                    }

                                    if (isFriendLimited) {
                                        Toast.makeText(baseContext, "Notifications unlimited from ${friendName.text}", Toast.LENGTH_SHORT).show()
                                        // Optional (recommended): if user enables limiting, reset the counter so they don’t get “stuck”
                                        notifPrefs().edit {
                                            putInt(unopenedKey(friendId), 0)
                                        }
                                    } else{
                                        Toast.makeText(baseContext, "Notifications limited from ${friendName.text}", Toast.LENGTH_SHORT).show()
                                    }

                                    true
                                }
                                else -> false
                            }
                        }
                        popup.show()
                    }

                     displayUnreadBubble(friendView, friendId, friendData)

                    groupChatList.addView(friendView)
                } else {
                    Log.d(TAG, "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception)
            }
        }
        // delete loading icon
        val loading : TextView = findViewById(R.id.groupchat_list_loading)
        (loading.parent as? ViewManager)?.removeView(loading)
    }

    fun displayUnreadBubble(friendView: View, friendId:String, friendData:Map<String, Any>?){
        // Keep or remove unread bubble based on if last message in chat is unread (and isn't from you)
        // filter for first message of dm
        db.collection(getChatIdWithFriend(friendId))
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(1)
        .get()
        .addOnSuccessListener { chatDocs ->
            for (chatDoc in chatDocs){
                if (chatDoc != null) {
                    val chatData = chatDoc.data
                    Log.d(TAG, "read:${chatData["read"] ?: ""}. newest message found in chat with ${friendData?.get("name") ?: "" }, '${chatDoc.data["text"] ?: ""}'" )
                    // If it's an old message without the "read" field, it will be assumed to be read
                    if (chatData["read"] == false       // If not read
                    && chatData["senderId"] != uid) {   // If you sent the last message, you've obviously read all the recent messages
                        // then display the unread bubble
                        val globalUnread : ImageView = friendView.findViewById(R.id.text_chat_unread_bubble)
                        globalUnread.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    fun getChatIdWithFriend(friendId:String) : String{
        val chatIdArray = arrayOf(uid ?: "me", friendId)
        chatIdArray.sort()
        val chatId = chatIdArray.joinToString("_")
        return "chats/$chatId/messages"
    }

    private fun defineBottomNavButtons() {
        // go to friends screen
        val friendButton: ImageButton = findViewById(R.id.menu_button_friends)
        friendButton.setOnClickListener {
            val intent = Intent(this, FriendAddActivity::class.java)
            startActivity(intent)
            finish()
        }

        // go to accounts screen
        val accountButton: ImageButton = findViewById(R.id.menu_button_account)
        accountButton.setOnClickListener {
            val intent = Intent(this, AccountActivity::class.java)
            startActivity(intent)
            finish()
        }

        // log out
        val logoutButton: ImageButton = findViewById(R.id.menu_button_logout)
        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
