package com.example.bonfire

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.component1
import com.google.firebase.storage.storage
import java.util.UUID


class ChatActivity : AppCompatActivity() {
    val TAG = "chat"
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val db = Firebase.firestore
    val helper = Helper()
    private var chatList: ArrayList<Map<String, Any>?> = arrayListOf()
    private var currentFriendId: String? = null

    private fun notifPrefs() = getSharedPreferences("notif_limits", MODE_PRIVATE)
    private fun unopenedKey(friendId: String) = "unopened_$friendId"
    private fun limitEnabledKey(friendId: String) = "limit_enabled_$friendId"
    private val OPEN_CHAT_KEY = "open_chat_friendId"
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var userData: Map<String, Object>
    var emojiMenuOpen = false
    var emojisPopulated = false
    var messagesPath = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.chat_layout)

        // read in friendId to open correct chat
        val b = intent.extras
        var friendId: String? = b?.getString("id") ?: ""
        if(friendId == ""){
            friendId = null
        }
        currentFriendId = friendId

        val limitPings: CheckBox = findViewById(R.id.limitPings)

        if (friendId != null) {
            // Load saved state
            val enabled = notifPrefs().getBoolean(limitEnabledKey(friendId), false)
            limitPings.isChecked = enabled

            // Save changes when user toggles
            limitPings.setOnCheckedChangeListener { _, isChecked ->
                notifPrefs().edit()
                    .putBoolean(limitEnabledKey(friendId), isChecked)
                    .apply()

                // Optional (recommended): if user enables limiting, reset the counter so they don’t get “stuck”
                if (isChecked) {
                    notifPrefs().edit().putInt(unopenedKey(friendId), 0).apply()
                }
            }
        } else {
            // No friendId? Disable the option
            limitPings.isEnabled = false
        }

        val recyclerView: RecyclerView = findViewById(R.id.chat_messages_RecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)


        // if friendId == null, we are in global chat
        messagesPath = "messages"
        if (isPrivateChat(friendId)){
            val chatIdArray = arrayOf(uid, friendId)
            chatIdArray.sort()
            val chatId = chatIdArray.joinToString("_")
            messagesPath = "chats/$chatId/messages"
        }

        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                uploadImageToFirebase(it, userData, recyclerView)
            }
        }

        val sendImageButton: ImageView = findViewById(R.id.chat_MessageBar_ImageButton)
        sendImageButton.setOnClickListener {
            messageSendDropList()
        }

        // get data of user so you don't have to request it every time
        val docRef = db.collection("users").document(uid?: "")
        docRef.get()
        .addOnSuccessListener { document ->
            if (document != null) {
                userData = document.data as Map<String, Object>
                createSendButton(userData, recyclerView)
                setChatName(friendId ?: "")
            } else {
                Log.d(TAG, "No such document")
            }
        }
        .addOnFailureListener { exception ->
            Log.d(TAG, "get failed with ", exception)
        }

        // Recycler view to display messages of chat //
        db.collection("users")
            .get()
            .addOnSuccessListener { result ->
                createData(friendId)
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "Error getting documents: ", exception)
            }

        // Button to switch to main screen activity
        val loginButton: ImageButton = findViewById(R.id.chat_cardView_backArrow)
        loginButton.setOnClickListener {
            val intent = Intent(this, GroupChatListActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Reset layout when keyboard pulls up
        val rootView = findViewById<View?>(R.id.chat_content)
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(
                rootView
            ) { v: View?, insets: WindowInsetsCompat? ->
                val imeInsets: Insets = insets!!.getInsets(WindowInsetsCompat.Type.ime())
                val navInsets: Insets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                val bottomInset: Int = imeInsets.bottom.coerceAtLeast(navInsets.bottom)

                recyclerView.scrollBy(0, bottomInset)
                Log.d("chatname", "scrolled by " + bottomInset)

                rootView.setPadding(
                    navInsets.left,
                    navInsets.top,
                    navInsets.right,
                    bottomInset
                )
                insets
            }
        }
    }

    fun setChatName(friendId:String){
        // Set chat name (name of person you're talking to)
        if (isPrivateChat(friendId)){
            // get name of friend
            val docRef = db.collection("users").document(friendId)
            docRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    //Log.d("chatname", "DocumentSnapshot data: ${document.data}")
                    val data = document.data
                    val chatName : TextView = findViewById(R.id.chat_cardView_UserName)
                    chatName.text = (data?.get("name") ?: "") as String

                    val friendAvatar : ShapeableImageView = findViewById(R.id.chat_cardView_UserIcon)
                    val avatar = (data?.get("avatar") ?: "") as String
                    helper.setProfilePicture(this, avatar, friendAvatar)
                } else {
                    Log.d(TAG, "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception)
            }
        }
    }

    fun messageSendDropList() {
        val optionsButton: ImageView = findViewById(R.id.chat_MessageBar_ImageButton)
        val emojiList: HorizontalScrollView = findViewById(R.id.emoji_list)

        optionsButton.setOnClickListener { view ->
            if (!emojisPopulated){
                populateEmojiList()
            }

            // if emoji menu already open, close it
            if (emojiMenuOpen) {
                closeEmojiList()
            }

            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.message_options_menu, popup.menu)

            // listener for when user clicks on message options dropdown menu
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_image -> {
                        imagePickerLauncher.launch("image/*")
                        true
                    }
                    R.id.action_emoji -> {
                        optionsButton.setImageResource(R.drawable.close)
                        emojiList.visibility = View.VISIBLE
                        emojiMenuOpen = true
                        true
                    }
                    R.id.action_record -> {
                        val voiceRecorder = VoiceRecorder()
                        voiceRecorder.openVoiceRecorderDialog(this)
                        true
                    }

                    else -> false
                }
            }
            popup.show()
        }
    }

    fun sendVoiceMessage(path:String){
        val messageData = hashMapOf(
            "displayName" to userData["name"],
            "photoURL" to userData["avatar"],
            "voiceURL" to path,
            "read" to false,
            "senderId" to uid,
            "text" to "",
            "timestamp" to Timestamp.now()
        )

        db.collection(messagesPath).document().set(messageData)

        val emailEditText: TextInputEditText = findViewById(R.id.chat_MessageBar_TextInputEditText)
        emailEditText.setText("")
        val recyclerView: RecyclerView = findViewById(R.id.chat_messages_RecyclerView)
        recyclerView.scrollToPosition(chatList.size - 1)

    }

    fun closeEmojiList(){
        val emojiList: HorizontalScrollView = findViewById(R.id.emoji_list)
        val optionsButton: ImageView = findViewById(R.id.chat_MessageBar_ImageButton)
        emojiList.visibility = View.GONE
        optionsButton.setImageResource(R.drawable.plus)
        emojiMenuOpen = false

    }

    /**
    Download all emojis in firebase Emojis/ and add to scrollview.
     */
    fun populateEmojiList(){
        val emojiList: LinearLayout = findViewById(R.id.emoji_list_linearLayout)
        val storage = Firebase.storage
        val listRef = storage.reference.child("Emojis")
        try{
            listRef.listAll().addOnSuccessListener { (items) ->
                for (item in items){
                    Log.d(TAG, item.path)
                    val gsReference = storage.getReferenceFromUrl(helper.firebasePath + "/" + item.path)
                    gsReference.downloadUrl.addOnSuccessListener { uri ->
                        // After successfully loading image from db, add image to scrollview
                        val emojiImage = ImageView(this)
                        val size = (70 * resources.displayMetrics.density).toInt()
                        val params = LinearLayout.LayoutParams(size, size)
                        emojiImage.layoutParams = params
                        Glide.with(this).load(uri).placeholder(R.drawable.default_pfp).into(emojiImage)
                        emojiList.addView(emojiImage)

                        emojiImage.setOnClickListener {
                            closeEmojiList()
                            sendImageMessage(uri.toString(), userData)
                        }
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Couldn't get avatar uri: $e")
                    }
                }
            }
        } catch (e : IllegalArgumentException){
            Log.e(TAG, "Couldn't load emojis: $e")
        }
        emojisPopulated = true
    }

    private fun uploadImageToFirebase(imageUri: Uri, userData:Map<String, Object>, recyclerView: RecyclerView) {
        val storageRef = Firebase.storage.reference
        val fileName = UUID.randomUUID().toString()
        val imageRef = storageRef.child("Chat_Media/$fileName")

        imageRef.putFile(imageUri)
        .addOnSuccessListener {
            // IMPORTANT: Get download URL
            imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                sendImageMessage(downloadUri.toString(), userData)
                val emailEditText: TextInputEditText = findViewById(R.id.chat_MessageBar_TextInputEditText)
                emailEditText.setText("")
                recyclerView.scrollToPosition(chatList.size - 1)
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Download URL error: $e")
            }

        }
        .addOnFailureListener { e ->
            Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Upload error: $e")
        }
    }

    private fun sendImageMessage(imageUrl: String, userData:Map<String, Object>) {
        val messageData = hashMapOf(
            "displayName" to userData["name"],
            "photoURL" to userData["avatar"],
            "imageUrl" to imageUrl,
            "read" to false,
            "senderId" to uid,
            "text" to "",
            "timestamp" to Timestamp.now()
        )

        db.collection(messagesPath).document().set(messageData)
    }

    fun createSendButton(userData:Map<String, Object>, recyclerView: RecyclerView){
        val sendButton: ImageView = findViewById(R.id.chat_MessageBar_SendButton)
        sendButton.setOnClickListener {
            val emailEditText: TextInputEditText = findViewById(R.id.chat_MessageBar_TextInputEditText)
            val messageSend = emailEditText.getText().toString()
            if (messageSend != "") {
                val messageData = hashMapOf(
                    "displayName" to userData["name"],
                    "photoURL" to userData["avatar"],
                    "read" to false,
                    "senderId" to uid,
                    "text" to messageSend,
                    "timestamp" to Timestamp.now()
                )
                db.collection(messagesPath).document().set(messageData)
            }
            emailEditText.setText("")
            recyclerView.scrollToPosition(chatList.size - 1)
        }
    }

    /**
     * Uses the repository to collect the raw data and bundles up those values
     * into our Message data class, something our adapter knows how to work with
     */
    private fun createData(friendId: String?){
        val db = Firebase.firestore

        // Generate chatId, which is the two users, combined
        // And generate messagesPath depending if global or private
        // Ex. userId = "abc" and friendId = "bcd" -> chatId = "abc_bcd"
        var chatId = ""
        var messagesPath = "messages"

        if (isPrivateChat(friendId)){
            val chatIdArray = arrayOf(uid, friendId)
            chatIdArray.sort()

            chatId = chatIdArray.joinToString("_")
            messagesPath = "chats/$chatId/messages"
        }
        Log.i(TAG, chatId)


        //val friendDocument = getDocumentReference(db, "users", friendId.toString())

        // Reads messages from chats/[chatId]/messages (global chat) into messageData arrayList
        //val messageData = ArrayList<Map<String, Any>?>()
        val messagesRef = db.collection(messagesPath).orderBy("timestamp")
        messagesRef.addSnapshotListener { snapshot, e ->
            chatList.clear()
            if (e != null) {
                Log.w(TAG, "Listen failed.", e)
            }

            if (snapshot != null && !snapshot.isEmpty) {
                for (document in snapshot.documents) {
                    // if someone else sent the message, mark it read
                    if (document.data?.get("senderId") != uid){
                        document.reference.update( mapOf(
                            "read" to true
                        ))
                    }

                    //Log.d(TAG, "data: ${document.data}")
                    chatList.add(document.data)
                }
            } else {
                Log.d(TAG, "data: null")
            }

            val recyclerView: RecyclerView = findViewById(R.id.chat_messages_RecyclerView)
            recyclerView.adapter = MessageAdapter(chatList, isPrivateChat(friendId), uid.toString())
            // scroll to bottom
            recyclerView.scrollToPosition(chatList.size - 1)
        }
    }

    fun isPrivateChat(friendId:String?) : Boolean{
        return !(friendId == null || friendId == "")
    }

    override fun onResume() {
        super.onResume()
        val friendId = currentFriendId ?: return
        notifPrefs().edit().putInt(unopenedKey(friendId), 0).apply()
        // mark this chat open
        notifPrefs().edit().putString(OPEN_CHAT_KEY, friendId).apply()
    }

    override fun onPause() {
        super.onPause()
        val friendId = currentFriendId ?: return

        // clear only if we are still the open chat (avoid races)
        if (notifPrefs().getString(OPEN_CHAT_KEY, null) == friendId) {
            notifPrefs().edit().remove(OPEN_CHAT_KEY).apply()
        }
    }

}