package com.example.bonfire

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore


class FriendAddActivity : AppCompatActivity() {
    val TAG = "friend add"
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val db = Firebase.firestore
    val helper = Helper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.friend_add_layout)

        val friendAddButton : Button = findViewById(R.id.friend_add_search_button)
        friendAddButton.setOnClickListener {
            val friendEditText: TextInputEditText = findViewById(R.id.friend_add_edit)
            val friendName = friendEditText.getText().toString()
            sendFriendRequest(friendName)
        }

        helper.listenForNotifs(uid ?: "", this)

        populateFriendRequestList()
        helper.defineBottomNavButtons(this)
    }

    fun populateFriendRequestList(){
        // generate pending friend requests sent to you
        val friendRequestLinearlayout : LinearLayout = findViewById(R.id.friend_request_linearLayout)
        val friendsRef = db.collection("friendRequests").whereEqualTo("to", uid.toString())
        friendsRef.get()
        .addOnSuccessListener { friendRequestDocs ->
            Log.d(TAG, "if (friendRequestDocs != null is ${friendRequestDocs != null}")
            if (!friendRequestDocs.isEmpty) {
                for (friendRequestDoc in friendRequestDocs){
                    val requesterUid = friendRequestDoc.data["from"] as String
                    Log.d(TAG, "if (friendRequestDocs != null is ${friendRequestDocs != null}")

                    generateFriendRequestView(requesterUid, friendRequestLinearlayout)
                }
            } else {
                // Add text that says you have no friend requests
                Log.d(TAG, "No friends haha")
                val noFriendText = TextView(this)
                noFriendText.text = "You have no pending friend requests."
                noFriendText.setPadding(16, 16, 16, 16)
                noFriendText.textAlignment = View.TEXT_ALIGNMENT_CENTER
                friendRequestLinearlayout.addView(noFriendText);
            }
        }
        .addOnFailureListener { exception ->
            Log.d(TAG, "get failed with ", exception)
        }
    }

    fun generateFriendRequestView(requesterUid:String, friendRequestLinearlayout: LinearLayout){
        Log.d(TAG, "generateFriendRequestView")
        // Get data of friend requester and generate view
        var requesterData : Map<String, Object>
        val requesterRef = db.collection("users").document(requesterUid)
        requesterRef.get()
        .addOnSuccessListener { document ->
            if (document != null) {
                // Generate view
                requesterData = document.data as Map<String, Object>
                val requestView = LayoutInflater.from(this).inflate(R.layout.friend_request_layout, null, false)

                val requesterName : TextView = requestView.findViewById(R.id.friend_request_name)
                requesterName.text = requesterData["name"] as String

                val requesterAvatar : ShapeableImageView = requestView.findViewById(R.id.text_chat_list_avatar)
                helper.setProfilePicture(this, requesterData["avatar"] as String, requesterAvatar)

                val acceptButton : ImageButton = requestView.findViewById(R.id.friend_request_yes)
                acceptButton.setOnClickListener{
                    addFriend(requesterUid)
                    (requestView.parent as ViewManager).removeView(requestView)
                }

                val declineButton : ImageButton = requestView.findViewById(R.id.friend_request_no)
                declineButton.setOnClickListener{
                    declineFriend(requesterUid)
                    (requestView.parent as ViewManager).removeView(requestView)
                }

                friendRequestLinearlayout.addView(requestView)
            } else {
                Log.d(TAG, "No such document")
            }
        }
        .addOnFailureListener { exception ->
            Log.d(TAG, "get failed with ", exception)
        }
    }

    fun addFriend(requesterUid:String){
        // add user to requester's friends
        val docRef = db.collection("users").document(requesterUid)
        docRef.update("friends", FieldValue.arrayUnion(uid.toString()))

        // add requester to user friend
        val userRef = db.collection("users").document(uid.toString())
        userRef.update("friends", FieldValue.arrayUnion(requesterUid))

        // ironically call declineFriend() (which just deletes the friend request document)
        // once you've accepted a friend request, you don't want it still there
        declineFriend(requesterUid)
    }

    fun declineFriend(requesterUid:String){
        // delete document in /friendRequests

        val friendRequestArray = arrayOf(uid, requesterUid)
        friendRequestArray.sort()
        val friendRequestId = friendRequestArray.joinToString("_")



        db.collection("friendRequests").document(friendRequestId)
            .delete()
            .addOnSuccessListener { Log.d(TAG, "DocumentSnapshot successfully deleted!") }
            .addOnFailureListener { e -> Log.w(TAG, "Error deleting document", e) }
    }

    fun sendFriendRequest(friendName:String){
        // validate
        if (friendName == "") {
            Toast.makeText(baseContext,"Please enter a username to search.", Toast.LENGTH_SHORT).show()
            return
        }

        // search for friend
        val docRef = db.collection("users").whereEqualTo("name", friendName)
        docRef.get()
        .addOnSuccessListener { documents ->
            if (!documents.isEmpty) {
                for (document in documents) {
                    if (friendRequestValid(document.data, document.id)){
                        // All validation passed, make friend request
                        val friendRequestArray = arrayOf(uid, document.id)
                        friendRequestArray.sort()
                        val friendRequestId = friendRequestArray.joinToString("_")

                        val messageData = hashMapOf(
                            "createdAt" to Timestamp.now(),
                            "from" to uid,
                            "status" to "pending",
                            "to" to document.id
                        )

                        // Write friend request to firebase
                        db.collection("friendRequests").document(friendRequestId).set(messageData)
                        Toast.makeText(baseContext, "Friend request sent successfully to ${document.data["name"]}", Toast.LENGTH_SHORT).show()
                    }

                }
            }
            else {
                Toast.makeText(baseContext, "No users found with that exact username.", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "No such document")
            }
        }
        .addOnFailureListener { exception ->
            Log.d(TAG, "get failed with ", exception)
        }
    }

    fun friendRequestValid(data: Map<String, Any>, documentId:String): Boolean{
        if (data.isEmpty() || documentId == "") return false

        // if you tried to friend yourself
        if(documentId == uid){
            Toast.makeText(baseContext, "Why are you trying to friend yourself?", Toast.LENGTH_SHORT).show()
            return false
        }

        // If found a user, check they're not already friends
        // if data["friends"] is null, that person has no friends, and cannot be friends with you
        if (data["friends"] != null){
            for (otherUserFriends in (data["friends"] as List<*>)){
                if (otherUserFriends == uid){
                    Toast.makeText(baseContext, "You are already friends with this user.", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Already friends with $uid")
                    return false
                }
            }
        }

        return true
    }
}