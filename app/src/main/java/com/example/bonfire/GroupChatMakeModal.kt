package com.example.bonfire

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage

/**
 * Modal popup for creating a group chat.
 */
@SuppressLint("UseSwitchCompatOrMaterialCode")
internal class GroupChatMakeModal {
    val addedFriendIDs = mutableListOf<String>()
    val helper = Helper()
    val db = Firebase.firestore
    val TAG = "Group chat make modal"
    lateinit var context: Context
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    lateinit var dialogView : View
    lateinit var createBtn : Button
    lateinit var switch18Plus : Switch
    lateinit var linearLayout : LinearLayout
    lateinit var editText :TextInputEditText


    fun openModal(context: Context, friendIDs: List<String>) {
        val activity = context as Activity
        dialogView = activity.layoutInflater.inflate(R.layout.groupchat_make_modal, null)

        this.context = context
        linearLayout = dialogView.findViewById(R.id.groupchat_make_modal_linearLayout)
        editText = dialogView.findViewById(R.id.groupChat_edit)
        switch18Plus = dialogView.findViewById(R.id.groupchat_make_18_switch)
        createBtn = dialogView.findViewById(R.id.createBtn)

        val builder = AlertDialog.Builder(context)
        val dialog = builder.create()
        dialog.setView(dialogView)
        dialog.show()

        // Immediately disable create button
        updateCreateButtonState()

        createBtn.setOnClickListener {
            createGroupChat(editText.text.toString())
            dialog.dismiss()
        }

        val closeBtn = dialogView.findViewById<Button>(R.id.closeBtn)
        closeBtn.setOnClickListener {
            dialog.cancel()
        }

        editText.addTextChangedListener {
            updateCreateButtonState()
        }

        generateListOfFriends(friendIDs)
        dialog.show()
    }

    private fun generateListOfFriends(friendIDs: List<String>) {
        for (friendID in friendIDs){
            // Get avatar of friend
            val docRef = db.collection("users").document(friendID)
            docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Create card of friend in list of friends to add
                    val friendView = LayoutInflater.from(context).inflate(R.layout.groupchat_make_friend_layout, linearLayout, false)
                    val friendData = document.data ?: return@addOnSuccessListener

                    // Friend data found, update avatar and name
                    val friendCard = friendView.findViewById<CardView>(R.id.card_friend_add_groupchat)
                    helper.setProfilePicture(context, (friendData["avatar"] ?: "") as String, friendView.findViewById<ImageView>(R.id.avatar))
                    val friendName = friendCard.findViewById<TextView>(R.id.friend_add_groupchat_name)
                    friendName.text = (friendData["displayName"] ?: "") as String
                    val friendCheck = friendCard.findViewById<CheckBox>(R.id.friend_add_groupchat_check_box)

                    friendCard.setOnClickListener {
                        // Toggle check box
                        friendCheck.isChecked = !friendCheck.isChecked
                        if (friendCheck.isChecked){
                            addFriend(document.id)
                        } else{
                            removeFriend(document.id)
                        }
                    }
                    linearLayout.addView(friendView)
                } else {
                    Log.d(TAG, "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception)
            }
        }
    }

    fun addFriend(friendID: String){
        addedFriendIDs.add(friendID)
        updateCreateButtonState()
    }

    fun removeFriend(friendID:String){
        addedFriendIDs.remove(friendID)
        updateCreateButtonState()
    }

    fun updateCreateButtonState(){
        // Enable create group chat button if group chat has two+ friends and a name
        if(addedFriendIDs.size >= 2
            && editText.text.toString().isNotEmpty()){
            createBtn.isEnabled = true
            createBtn.isClickable = true
        } else{
            createBtn.isEnabled = false
            createBtn.isClickable = false
        }
    }

    private fun createGroupChat(chatName: String) {
        // don't forget to include yourself in the group chat!
        addedFriendIDs.add(uid.toString())

        val storage = Firebase.storage
        try {
            // Get URI of default profile picture
            val avatarPath = helper.firebasePath + "/Profile_Pictures/logo.png"
            val groupChatPath = "groupChats"
            val gsReference = storage.getReferenceFromUrl(avatarPath)
            gsReference.downloadUrl.addOnSuccessListener { uri ->
                val messageData = hashMapOf(
                    "avatar" to uri,
                    "createdAt" to Timestamp.now(),
                    "createdBy" to uid,
                    "memberIds" to addedFriendIDs,
                    "is18Plus" to switch18Plus.isChecked,
                    "name" to chatName,
                    "type" to "group"
                )

                db.collection(groupChatPath).document().set(messageData)
            }
        } catch (e : IllegalArgumentException){
            Log.e(TAG, "Icon not found: $e")
        }
    }
}