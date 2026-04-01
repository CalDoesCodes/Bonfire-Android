package com.example.bonfire

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import androidx.core.view.size
import com.bumptech.glide.Glide
import com.google.firebase.storage.storage
import androidx.core.content.edit
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.SetOptions


class AccountActivity : AppCompatActivity() {
    private var TAG: String = "account_activity"
    private val helper = Helper()
    private val channelId = "i.apps.notifications" // Unique channel ID for notifications
    private val description = "Message notification"  // Description for the notification channel
    private var uuid = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.account_layout)

        val notificationChannel = NotificationChannel(
            channelId,
            description,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableLights(true) // Turn on notification light
            lightColor = Color.GREEN
            enableVibration(true) // Allow vibration for notifications
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)

        val user = FirebaseAuth.getInstance().currentUser
        val accountUserText: TextView = findViewById(R.id.account_name)
        val accountDisplayUserText: TextView = findViewById(R.id.account_displayName)
        val accountEmailText: TextView = findViewById(R.id.account_email)
        val accountBioText : TextView = findViewById(R.id.account_bio)
        val accountAvatarImageView: ShapeableImageView = findViewById(R.id.account_avatar)

        val displaynameEditText : TextInputEditText = findViewById(R.id.displayname_editText)
        val bioEditText : TextInputEditText = findViewById(R.id.bio_editText)

        uuid = user?.uid ?: ""
        val db = Firebase.firestore
        // get details of account
        if (uuid != "") {
            val userRef = db.collection("users").document(uuid)
            userRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Log.d(TAG, "DocumentSnapshot data: ${document.data}")
                    val data = document.data

                    // Update views to display accurate user info on screen
                    accountEmailText.text = (data?.get("email") ?: "") as String
                    accountUserText.text = (data?.get("name") ?: "") as String

                    var displayName = (data?.get("displayName") ?: "") as String
                    accountDisplayUserText.text = displayName
                    displaynameEditText.setText(displayName)

                    var bio = (data?.get("bio") ?: "") as String
                    accountBioText.text = bio
                    bioEditText.setText(bio)


                    // button listener to update user data when pressed
                    val userSaveButton : Button = findViewById(R.id.user_save_button)
                    userSaveButton.setOnClickListener {
                        bio = (bioEditText.text ?: "").toString()
                        accountBioText.text = bio
                        displayName = (displaynameEditText.text ?: "").toString()
                        accountDisplayUserText.text = displayName

                        val updatedUserData = hashMapOf(
                            "displayName" to displayName,
                            "bio" to bio
                        )
                        userRef.set(updatedUserData, SetOptions.merge())
                    }

                    addDarkLightModeButtonListeners()

                    // Create a reference to a file from a Google Cloud Storage URI
                    val avatarPath = (data?.get("avatar") ?: "") as String

                    helper.setProfilePicture(this, avatarPath, accountAvatarImageView)
                } else {
                    Log.d(TAG, "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d(TAG, "get failed with ", exception)
            }
        } else{
            Log.e(TAG, "User uid null")
        }

        val storagePath = helper.firebasePath + "/Profile_Pictures/"
        val avatarGrid: GridLayout = findViewById(R.id.account_grid)
        for (i in 0..<avatarGrid.size) {
            val child: ShapeableImageView = (avatarGrid as ViewGroup).getChildAt(i) as ShapeableImageView
            child.setOnClickListener {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener

                // Update the "avatar" field of the user 
                val userRef = db.collection("users").document(currentUid)
                val avatarUri = storagePath + "icon${(i + 1)}.png"

                val storage = Firebase.storage
                try {
                    val gsReference = storage.getReferenceFromUrl(avatarUri)

                    gsReference.downloadUrl.addOnSuccessListener { uri ->
                        if (isDestroyed || isFinishing) return@addOnSuccessListener
                        // visually change icon
                        Glide.with(this)
                            .load(uri)
                            .into(accountAvatarImageView)

                        // change field in db
                        userRef
                            .update("avatar", uri.toString())
                            .addOnSuccessListener { Log.d(TAG, "DocumentSnapshot successfully updated!") }
                            .addOnFailureListener { e -> Log.w(TAG, "Error updating document", e) }
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Couldn't get avatar uri: $e")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error with avatar URI: $e")
                }
            }
        }

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        helper.listenForNotifs(uid ?: "", this)

        populateBlockedList()
        helper.defineBottomNavButtons(this)
    }

    /**
     * StylePreferences : light / dark / system
     */
    private fun addDarkLightModeButtonListeners() {
        val darkButton: Button = findViewById(R.id.mode_btn_dark)
        val lightButton: Button = findViewById(R.id.mode_btn_light)
        val systemButton: Button = findViewById(R.id.mode_btn_system)
        applyDarkLightModeButtonListener(darkButton, AppCompatDelegate.MODE_NIGHT_YES, "dark")
        applyDarkLightModeButtonListener(lightButton, AppCompatDelegate.MODE_NIGHT_NO, "light")
        applyDarkLightModeButtonListener(systemButton, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, "system")
    }

    private fun applyDarkLightModeButtonListener(button:Button, stylePreferenceInt:Int, stylePreference:String){
        val db = Firebase.firestore
        button.apply{
            setOnClickListener {
                // set actual night mode
                AppCompatDelegate.setDefaultNightMode(stylePreferenceInt)

                // reflect change in database
                val userRef = db.collection("users").document(uuid)
                userRef
                    .update("stylePreference", stylePreference)
                    .addOnSuccessListener { Log.d(TAG, "DocumentSnapshot successfully updated for user $uuid!") }
                    .addOnFailureListener { e -> Log.w(TAG, "Error updating document", e) }
            }
        }
    }

    private fun populateBlockedList() {
        val blockedPref = getSharedPreferences("blocked", MODE_PRIVATE)
        val mutedPref = getSharedPreferences("muted", MODE_PRIVATE)
        val blockedLayout: LinearLayout = findViewById(R.id.account_blocked_list)
        blockedLayout.removeAllViews()

        val allEntries = blockedPref.all
        for ((key, value) in allEntries) {
            // Check if key is a user ID (not starting with name_)
            if (value is Boolean && value && !key.startsWith("name_")) {
                val friendId = key
                val friendName = blockedPref.getString("name_$friendId", "Unknown") ?: "Unknown"

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    setPadding(0, 8, 0, 8)
                }

                val nameText = TextView(this).apply {
                    text = friendName
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setTextColor(Color.WHITE)
                    textSize = 18f
                }

                val unblockButton = Button(this).apply {
                    text = "Unblock"
                    setOnClickListener {
                        blockedPref.edit {
                            remove(friendId)
                            remove("name_$friendId")
                        }
                        // Also unmute the user when unblocking
                        mutedPref.edit {
                            remove(friendId)
                        }
                        populateBlockedList()
                        Toast.makeText(this@AccountActivity, "Unblocked $friendName", Toast.LENGTH_SHORT).show()
                    }
                }

                row.addView(nameText)
                row.addView(unblockButton)
                blockedLayout.addView(row)
            }
        }
    }

}
