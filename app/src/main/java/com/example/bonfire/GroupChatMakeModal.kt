package com.example.bonfire

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.util.UUID
import java.io.File

/**
 * Modal popup for creating a group chat.
 */
internal class GroupChatMakeModal {
    val addedFriendIDs = mutableListOf<String>()
    val helper = Helper()
    lateinit var dialogView : View
    lateinit var createBtn : Button
    val db = Firebase.firestore
    val TAG = "Group chat make modal"
    lateinit var context: Context
    lateinit var linearLayout : LinearLayout


    fun openModal(context: Context, friendIDs: List<String>) {
        val activity = context as Activity
        dialogView = activity.layoutInflater.inflate(R.layout.groupchat_make_modal, null)
        this.context = context
        this.linearLayout = context.findViewById(R.id.groupchat_make_modal_linearLayout)

        val builder = AlertDialog.Builder(context)
        val dialog = builder.create()
        dialog.setView(dialogView)
        dialog.show()

        createBtn = dialogView.findViewById(R.id.createBtn)
        createBtn.setOnClickListener {
            if(createBtn.isEnabled) {
                createGroupChat()
                dialog.dismiss()
            }
        }

        val closeBtn = dialogView.findViewById<Button>(R.id.closeBtn)
        closeBtn.setOnClickListener {
            dialog.cancel()
        }

        generateListOfFriends(friendIDs)

        dialog.show()
    }

    private fun generateListOfFriends(friendIDs: List<String>) {
        val friendView = LayoutInflater.from(context).inflate(R.layout.friend_add_groupchat_layout, linearLayout, false)

        for (friendID in friendIDs){
            // Get avatar of friend
            val docRef = db.collection("users").document(friendID)
            docRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val friendData = document.data ?: return@addOnSuccessListener
                    // Friend data found, update avatar
                    helper.setProfilePicture(context, (friendData["avatar"] ?: "") as String, friendView.findViewById<ImageView>(R.id.avatar))
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
        if(addedFriendIDs.size >= 2){
            markButtonEnable()
        }
    }

    fun removeFriend(friendID:String){
        addedFriendIDs.remove(friendID)
        if(addedFriendIDs.size < 2){
            markButtonDisable()
        }
    }

    fun markButtonDisable() {
        createBtn.isEnabled = false
        createBtn.isClickable = false
        createBtn.text = ""
    }

    fun markButtonEnable() {
        createBtn.isEnabled = true
        createBtn.isClickable = true
        createBtn.text = "Create group chat"
    }

    private fun createGroupChat() {
        TODO("Not yet implemented")
    }
}