package com.example.bonfire

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

data class FriendWithTimestamp(
    val friendId: String,
    val friendData: Map<String, Any>,
    val latestTimestamp: Long
)

class GroupChatListActivity : AppCompatActivity() {
    val TAG = "GroupChatListActivity"
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val db = Firebase.firestore
    val helper = Helper()
    lateinit var userData  : Map<String, Any>
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
                    generateOpenChatButton(globalChat.findViewById<CardView>(R.id.card_chat_list_message), "", ChatType.GLOBAL, "Global Chat", "")

                    userData = document.data!!
                    // Get all user friends and call populateFriendList() to create cards for each private chat
                    val userFriends = userData?.get("friends") as? List<*>

                    Log.d(TAG, "user friend list found")
                    if (!userFriends.isNullOrEmpty()){
                        @Suppress("UNCHECKED_CAST")
                        // dynamically generate friend view in list
                        populateFriendList(db, userFriends as List<String>)
                        populateGroupChatList(db)

                        // create group chat button
                        val createGroupChat = findViewById<View>(R.id.make_groupchat)
                        createGroupChat.setOnClickListener {
                            val groupChatMakeModal = GroupChatMakeModal()
                            groupChatMakeModal.openModal(this, userFriends)
                        }
                    } else{
                        // Add text if user has no friends
                        val groupChatList : LinearLayout = findViewById(R.id.pinned_LinearLayout)
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
     * sorts by recent message
     *
     * @param db: Firebase database to get backend information from
     * @param userFriends: List of friend ids
     */
    private fun populateFriendList(db: FirebaseFirestore, userFriends:List<String>) {
        val blockedPref : SharedPreferences = getSharedPreferences("blocked", MODE_PRIVATE)
        val pinPrefs : SharedPreferences = getSharedPreferences("pins", MODE_PRIVATE)

        val friendList = mutableListOf<FriendWithTimestamp>()
        var remaining = userFriends.size

        if (remaining == 0) return

        for (friendId in userFriends) {

            if (blockedPref.getBoolean(friendId, false)) {
                remaining--
                continue
            }

            val userRef = db.collection("users").document(friendId)

            userRef.get().addOnSuccessListener { userDoc ->
                val friendData = userDoc.data ?: emptyMap()

                // Build chatId
                val chatIdArray = arrayOf(uid ?: "me", friendId)
                chatIdArray.sort()
                val chatId = chatIdArray.joinToString("_")

                // Query latest message directly
                db.collection("chats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { msgDocs ->
                    val latestTimestamp = if (!msgDocs.isEmpty) {
                        val ts = msgDocs.documents[0].getTimestamp("timestamp")
                        ts?.toDate()?.time ?: 0L
                    } else 0L

                    friendList.add(
                        FriendWithTimestamp(friendId, friendData, latestTimestamp)
                    )

                    remaining--
                    if (remaining == 0) {
                        friendList.sortByDescending { it.latestTimestamp }

                        for (friend in friendList) {
                            val linearLayout : LinearLayout
                            // category
                            if (pinPrefs.getBoolean(friend.friendId, false)){
                                linearLayout = findViewById(R.id.pinned_LinearLayout)
                            } else{
                                linearLayout = findViewById(R.id.list_messages_LinearLayout_notpinned)
                            }

                            addFriendView(
                                friend.friendData,
                                friend.friendId,
                                blockedPref,
                                linearLayout
                            )
                        }
                    }
                }
            }
        }
    }


    /**
     * Generate list of group chats that include the user, with a button that will open the group chat
     * also sorts by most recent message
     *
     * @param db: Firebase database to get backend information from
     */
    fun populateGroupChatList(db: FirebaseFirestore) {
        val pinPrefs : SharedPreferences = getSharedPreferences("pins", MODE_PRIVATE)

        val groupChatRef = db.collection("groupChats")
            .whereArrayContains("memberIds", uid!!)

        groupChatRef.get()
        .addOnSuccessListener { documents ->

            val chatList = mutableListOf<Pair<Map<String, Any>, Pair<String, Long>>>()
            var remaining = documents.size()

            if (remaining == 0) return@addOnSuccessListener

            for (document in documents) {
                val chatData = document.data
                val chatId = document.id

                db.collection("groupChats")
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { msgDocs ->

                    val latestTimestamp = if (!msgDocs.isEmpty) {
                        val ts = msgDocs.documents[0].getTimestamp("timestamp")
                        ts?.toDate()?.time ?: 0L
                    } else {
                        0L
                    }

                    chatList.add(chatData to (chatId to latestTimestamp))

                    remaining--
                    if (remaining == 0) {
                        // All queries finished → sort + render
                        chatList.sortByDescending { it.second.second }

                        for ((data, pair) in chatList) {
                            val id = pair.first

                            val linearLayout : LinearLayout
                            // category
                            if (pinPrefs.getBoolean(id, false)){
                                linearLayout = findViewById(R.id.pinned_LinearLayout)
                            } else{
                                linearLayout = findViewById(R.id.groupchat_LinearLayout_notpinned)
                            }

                            addGroupChatView(data, id, linearLayout)
                        }
                    }
                }
                .addOnFailureListener {
                    remaining--
                }
            }
        }
        .addOnFailureListener { exception ->
            Log.d(TAG, "group chat get failed with ", exception)
        }
    }

    fun isOver18(isoString: String): Boolean {
        val birthDate = Instant.parse(isoString)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

        val today = LocalDate.now()
        val age = Period.between(birthDate, today).years

        return age >= 18
    }

    /**
     * Generate friend view and add it to groupChatList
     *
     */
    fun addFriendView(friendData:Map<String, Any>, chatId:String, blockedPref: SharedPreferences, groupChatList: LinearLayout){
        val friendView = LayoutInflater.from(this).inflate(R.layout.groupchat_layout, groupChatList, false)

        val friendName : TextView = friendView.findViewById(R.id.text_chat_list_user)
        friendName.text = (friendData["displayName"] ?: "Anonymous").toString()

        val friendAvatarView : ShapeableImageView = friendView.findViewById(R.id.text_chat_list_avatar)
        val avatar = (friendData["avatar"] ?: "").toString()
        helper.setProfilePicture(this, avatar, friendAvatarView)

        // Generate button listener that will open chat with friend
        generateOpenChatButton(friendView, chatId, ChatType.PRIVATE, avatar, friendName.text as String)

        // button listener to show dropdown menu for options
        val optionsButton: ImageButton = friendView.findViewById(R.id.chat_options)
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
        val groupChatView = LayoutInflater.from(this).inflate(R.layout.groupchat_layout, groupChatList, false)

        val friendName : TextView = groupChatView.findViewById(R.id.text_chat_list_user)
        friendName.text = (friendData["name"] ?: "Group Chat").toString()

        val friendAvatarView : ShapeableImageView = groupChatView.findViewById(R.id.text_chat_list_avatar)
        val avatar = (friendData["avatar"] ?: "").toString()
        helper.setProfilePicture(this, avatar, friendAvatarView)

        // Generate button listener that will open chat with friend
        val chatIs18Plus : Boolean = (friendData["is18Plus"] ?: false) as Boolean
        generateOpenChatButton(groupChatView, chatId, ChatType.GROUP, avatar, friendName.text as String, chatIs18Plus)

        val groupChatOwner : String = (friendData["createdBy"] ?: "") as String

        // generate options menu 3 dots on click listener
        val optionsButton = groupChatView.findViewById<ImageButton>(R.id.chat_options)
        setUpOptionsGroupChatButton(optionsButton, chatId, groupChatList, groupChatView, groupChatOwner)

        //Add the friendView to the groupChatList parent Linear Layout
        groupChatList.addView(groupChatView)
    }

    fun setUpOptionsGroupChatButton(optionsButton: ImageButton, groupChatId: String, groupChatList : LinearLayout,
                                    groupChatView: View, groupChatOwner: String){
        optionsButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.groupchat_options_menu, popup.menu)

            val pinPrefs : SharedPreferences = getSharedPreferences("pins", MODE_PRIVATE)
            var chatPinned = pinPrefs.getBoolean(groupChatId, false)
            val pinItem = popup.menu.findItem(R.id.action_pin)
            pinItem.title = if (chatPinned) "Unpin" else "Pin"

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    // Delete group chat button
                    R.id.action_delete -> {
                        // "wait are you even the owner of the group chat?"
                        if(uid == groupChatOwner){
                            // "Are you sure?" pop up
                            val dialogClickListener: DialogInterface.OnClickListener =
                                DialogInterface.OnClickListener { dialog, which ->
                                    when (which) {
                                        DialogInterface.BUTTON_POSITIVE -> {
                                            // really do it
                                            db.collection("groupChats").document(groupChatId)
                                                .delete()
                                                .addOnSuccessListener { Log.d(TAG, "Group chat successfully deleted!") }
                                                .addOnFailureListener { e -> Log.w(TAG, "Error deleting group chat", e) }

                                            // remove in list
                                            groupChatList.removeView(groupChatView)

                                        }
                                        DialogInterface.BUTTON_NEGATIVE -> {
                                            true
                                        }
                                    }
                                }
                            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                            builder.setMessage("Delete this group chat? This cannot be undone.")
                                .setPositiveButton("Yes", dialogClickListener)
                                .setNegativeButton("No", dialogClickListener).show()

                        } else{
                            helper.makeToast(this, "You are not the owner of this group chat.")
                        }
                        true
                    }
                    R.id.action_pin -> {
                        chatPinned = !chatPinned

                        val pinned_linearLayout : LinearLayout = findViewById(R.id.pinned_LinearLayout)
                        val unpinned_linearLayout : LinearLayout = findViewById(R.id.list_messages_LinearLayout_notpinned)

                        // toggle pin and move to correct linearlayout if toggled

                        val parent = groupChatView.parent as? ViewGroup
                        parent?.removeView(groupChatView)

                        // not pinned -> pinned
                        if (chatPinned) {
                            pinned_linearLayout.addView(groupChatView)
                        }
                        // pinned -> not pinned
                        else{
                            unpinned_linearLayout.addView(groupChatView)
                        }

                        pinPrefs.edit {
                            putBoolean(groupChatId, chatPinned)
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

            val pinPrefs : SharedPreferences = getSharedPreferences("pins", MODE_PRIVATE)
            var chatPinned = pinPrefs.getBoolean(friendId, false)
            val pinItem = popup.menu.findItem(R.id.action_pin)
            pinItem.title = if (chatPinned) "Unpin" else "Pin"

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
                    R.id.action_pin -> {
                        chatPinned = !chatPinned

                        val pinned_linearLayout : LinearLayout = findViewById(R.id.pinned_LinearLayout)
                        val unpinned_linearLayout : LinearLayout = findViewById(R.id.groupchat_LinearLayout_notpinned)

                        // toggle pin and move to correct linearlayout if toggled

                        val parent = friendView.parent as? ViewGroup
                        parent?.removeView(friendView)

                        // not pinned -> pinned
                        if (chatPinned) {
                            pinned_linearLayout.addView(friendView)
                        }
                        // pinned -> not pinned
                        else{
                            unpinned_linearLayout.addView(friendView)
                        }

                        pinPrefs.edit {
                            putBoolean(friendId, chatPinned)
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
    private fun generateOpenChatButton(friendView: View, friendId: String?, chatType: ChatType, avatar:String, name:String, isExplicit: Boolean = false) : CardView {
        val button = friendView.findViewById<CardView>(R.id.card_chat_list_message)
        Log.d(TAG, "Sets up the button to open a specific chat $chatType $friendId $avatar $name")

        button.setOnClickListener {
            if(isExplicit && !isOver18((userData["birthDate"] ?: "2017-03-06T06:00:00.000Z").toString())){
                check18PlusConsent(friendView, friendId.toString(), chatType, avatar, name)
            } else{
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
        }
        return button
    }

    fun check18PlusConsent(groupChatView: View, chatId:String, chatType: ChatType, avatar:String, name:String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Sensitive Content")
        builder.setMessage("This group chat has sensitive content. Are you 18 years or older?")

        builder.setPositiveButton("Yes") { _, _ ->
            val intent = Intent(this, ChatActivity::class.java)

            // User agreed, run the success code
            // Generate button listener that will open chat with friend
            // Passes friendID to chat activity
            intent.putExtra("com.example.bonfire.chatType", chatType.toString())
            intent.putExtra("com.example.bonfire.id", chatId)
            intent.putExtra("com.example.bonfire.avatar", avatar)
            intent.putExtra("com.example.bonfire.name", name)

            ContextCompat.startActivity(this, intent, null)
            finish()
        }

        builder.setNegativeButton("No") { _, _ ->
            // User denied, perform cleanup
            handleUserRemoval(chatId, groupChatView.parent as LinearLayout, groupChatView)
        }

        builder.setCancelable(false) // Prevent clicking outside the box to bypass
        builder.show()
    }

    private fun handleUserRemoval(groupChatId:String, groupChatList:LinearLayout, groupChatView: View) {
        val groupChatRef = db.collection("groupChats").document(groupChatId)
        // This removes the specific ID from the "members" array
        groupChatRef.update("memberIds", FieldValue.arrayRemove(uid))
            .addOnSuccessListener {
                Log.d(TAG, "Group chat successfully deleted! $groupChatId")
                helper.makeToast(this, "You have been removed.")
                groupChatList.removeView(groupChatView)
            }
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
