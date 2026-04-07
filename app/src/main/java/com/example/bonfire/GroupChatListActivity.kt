package com.example.bonfire

import android.content.Intent
import android.content.SharedPreferences
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
    val TAG = "GroupChatListActivity"
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val db = Firebase.firestore
    val helper = Helper()
    private fun notifPrefs() = getSharedPreferences("notif_limits", MODE_PRIVATE)
    private fun limitEnabledKey(friendId: String) = "limit_enabled_$friendId"
    private fun unopenedKey(friendId: String) = "unopened_$friendId"


    /**
     * Does the setup for the main page (which is the list of chats for a user)
     *
     * @param savedInstanceState: Bundle for passing into super.onCreate(), can be null
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.groupchat_list_layout)

        if (!uid.isNullOrEmpty()) {
            val userRef = db.collection("users").document(uid)
            userRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    //Remove groupchat_list_loading view
                    val loading : TextView = findViewById(R.id.groupchat_list_loading)
                    (loading.parent as? ViewManager)?.removeView(loading)

                    // Open global chat button
                    val globalChat = findViewById<View>(R.id.global_chat)
                    generateOpenChatButton(globalChat.findViewById<CardView>(R.id.card_chat_list_message), null, ChatType.GLOBAL, "Global Chat", "")

                    val userData = document.data
                    // Get all user friends and call populateFriendList() to create cards for each private chat
                    val userFriends = userData?.get("friends") as? List<*>
                    Log.d(TAG, "user friend list found")
                    if (!userFriends.isNullOrEmpty()){
                        @Suppress("UNCHECKED_CAST")
                        // dynamically generate friend view in list
                        val groupChatList : LinearLayout = findViewById(R.id.list_messages_LinearLayout)

                        populateFriendList(db, userFriends as List<String>, groupChatList)
                        populateGroupChatList(db, groupChatList)
                    } else{
                        // Add text if user has no friends
                        val groupChatList : LinearLayout = findViewById(R.id.list_messages_LinearLayout)
                        val noFriendText = TextView(this)
                        noFriendText.text = "You have no friends. Send a request!"
                        noFriendText.setPadding(24, 24, 24, 24)
                        noFriendText.textSize = 20.toFloat()
                        noFriendText.textAlignment = View.TEXT_ALIGNMENT_CENTER
                        groupChatList.addView(noFriendText)
                    }
                    Log.d(TAG, "${userFriends.toString()} user friend list found")
                } else {
                    Log.d(TAG, "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception)
            }
        }
        helper.listenForNotifs(uid ?: "", this)
        helper.defineBottomNavButtons(this)

    }

    /**
     * Generate list of friends, with a button that will open the specific private message with them
     *
     * @param db: Firebase database to get backend information from
     * @param userFriends: List of friend ids
     */
    private fun populateFriendList(db: FirebaseFirestore, userFriends:List<String>, groupChatList: LinearLayout) {
        val blockedPref : SharedPreferences = getSharedPreferences("blocked", MODE_PRIVATE)

        for (friendId in userFriends) {
            // Skip if blocked
            if (blockedPref.getBoolean(friendId, false)) continue

            Log.d(TAG, "friendId $friendId")

            // Find data of friend
            val docRef = db.collection("users").document(friendId)
            docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val friendData = document.data ?: return@addOnSuccessListener
                    // Friend data found
                    addFriendView(friendData, friendId, blockedPref, groupChatList)
                } else {
                    Log.d(TAG, "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception)
            }
        }
    }


    /**
     * Generate list of group chats that include the user, with a button that will open the group chat
     *
     * @param db: Firebase database to get backend information from
     */
    fun populateGroupChatList(db: FirebaseFirestore, groupChatList: LinearLayout){
        // Return all group chats that include the user
        val groupChatRef = db.collection("groupChats").whereArrayContains("memberIds", uid!!)
        groupChatRef.get()
        .addOnSuccessListener { documents ->
            for (document in documents){
                val chatData = document.data
                addGroupChatView(chatData, document.id, groupChatList)
            }
        }
        .addOnFailureListener { exception ->
            Log.d(TAG, "group chat get failed with ", exception)
        }
    }

    /**
     * Generate friend view and add it to groupChatList
     *
     */
    fun addFriendView(friendData:Map<String, Any>, chatId:String, blockedPref: SharedPreferences, groupChatList: LinearLayout){
        val friendView = LayoutInflater.from(this).inflate(R.layout.groupchat_layout, groupChatList, false)

        val friendName : TextView = friendView.findViewById(R.id.text_chat_list_user)
        friendName.text = (friendData["name"] ?: "Anonymous").toString()

        val friendAvatarView : ShapeableImageView = friendView.findViewById(R.id.text_chat_list_avatar)
        val avatar = (friendData["avatar"] ?: "").toString()
        helper.setProfilePicture(this, avatar, friendAvatarView)

        // Generate button listener that will open chat with friend
        generateOpenChatButton(friendView, chatId, ChatType.PRIVATE, avatar, friendName.text as String)

        // button listener to show dropdown menu for options
        val optionsButton: ImageButton = friendView.findViewById(R.id.text_chat_list_message_options)
        setUpOptionsButton(optionsButton, chatId, friendName.text, groupChatList,  blockedPref, friendView)

        displayUnreadBubble(friendView, chatId, friendData)

        //Add the friendView to the groupChatList parent Linear Layout
        groupChatList.addView(friendView)
    }

    /**
     * Generate group chat view and add it to groupChatList
     *
     */
    fun addGroupChatView(friendData:Map<String, Any>, chatId:String, groupChatList: LinearLayout){
        val friendView = LayoutInflater.from(this).inflate(R.layout.groupchat_layout, groupChatList, false)

        val friendName : TextView = friendView.findViewById(R.id.text_chat_list_user)
        friendName.text = (friendData["name"] ?: "Group Chat").toString()

        val friendAvatarView : ShapeableImageView = friendView.findViewById(R.id.text_chat_list_avatar)
        val avatar = (friendData["avatar"] ?: "").toString()
        helper.setProfilePicture(this, avatar, friendAvatarView)

        // Generate button listener that will open chat with friend
        generateOpenChatButton(friendView, chatId, ChatType.GROUP, avatar, friendName.text as String)

        //Add the friendView to the groupChatList parent Linear Layout
        groupChatList.addView(friendView)
    }

    /**
     * Sets up the options button (three vertical dots) for a friend for chatting
     *
     * @param optionsButton the button that is getting functionality setup
     * @param friendId the Id String for the friend related to the chat in question
     * @param friendName name of the friend
     * @param groupChatList Layout this button is being added to
     * @param blockedPref current preference for blocking of the given friend
     * @param friendView view that will be removed from the page if user chooses to block
     */
    private fun setUpOptionsButton(optionsButton: ImageButton, friendId: String, friendName: CharSequence,
                                   groupChatList : LinearLayout, blockedPref : SharedPreferences, friendView: View){

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
                            Toast.makeText(baseContext, "${friendName} has been unmuted.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(baseContext, "${friendName} has been muted.", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                    R.id.action_block -> {
                        blockedPref.edit {
                            putBoolean(friendId, true)
                            putString("name_$friendId", friendName.toString())
                        }
                        // Also mute the user when blocking
                        sharedPref.edit {
                            putInt(friendId, 1)
                        }
                        groupChatList.removeView(friendView)
                        Toast.makeText(baseContext, "Blocked ${friendName}", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.action_limit -> {
                        // Save changes when user toggles
                        notifPrefs().edit{
                            putBoolean(limitEnabledKey(friendId), !isFriendLimited)
                        }

                        if (isFriendLimited) {
                            Toast.makeText(baseContext, "Notifications unlimited from ${friendName}", Toast.LENGTH_SHORT).show()
                            // Optional (recommended): if user enables limiting, reset the counter so they don’t get “stuck”
                            notifPrefs().edit {
                                putInt(unopenedKey(friendId), 0)
                            }
                        } else{
                            Toast.makeText(baseContext, "Notifications limited from ${friendName}", Toast.LENGTH_SHORT).show()
                        }

                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    /**
     * Sets up the button to open a specific chat
     *
     * @param friendView view that the button is targeting
     * @param friendId Id of friend to find the chat to open
     */
    private fun generateOpenChatButton(friendView: View, friendId: String?, chatType: ChatType, avatar:String, name:String) : CardView {
        val button = friendView.findViewById<CardView>(R.id.card_chat_list_message)
        Log.d(TAG, "Sets up the button to open a specific chat $chatType $friendId $avatar $name")

        button.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)

            // Passes friendID to chat activity
            if (friendId != null) {
                intent.putExtra("com.example.bonfire.chatType", chatType.toString())
                intent.putExtra("com.example.bonfire.id", friendId)
                intent.putExtra("com.example.bonfire.avatar", avatar)
                intent.putExtra("com.example.bonfire.name", name)
            }

            ContextCompat.startActivity(this, intent, null)
            finish()
        }
        return button
    }


    /**
     * Displays a red dot next to chats from users who've sent messages
     *
     * @param friendView view that will get the red dot
     * @param friendId friend's id for assignment checking
     * @param friendData all data from friend
     */
    fun displayUnreadBubble(friendView: View, friendId:String, friendData:Map<String, Any>?){
        // Keep or remove unread bubble based on if last message in chat is unread (and isn't from you)
        // filter for first message of dm
        db.collection(getChatIdWithFriend(friendId))
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(1)
        .get()
        .addOnSuccessListener { chatDocs ->
            for (chatDoc in chatDocs){
                if (chatDoc != null && chatDoc.exists()) {
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

    /**
     * Gets the chatId given a friend's id string
     *
     * @param friendId Identifier string for a given friend
     */
    fun getChatIdWithFriend(friendId:String) : String{
        val chatIdArray = arrayOf(uid ?: "me", friendId)
        chatIdArray.sort()
        val chatId = chatIdArray.joinToString("_")
        return "chats/$chatId/messages"
    }

}
