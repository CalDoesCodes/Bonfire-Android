package com.example.bonfire

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.startActivity
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlin.jvm.java

public class Helper {
    private val TAG = "Helper"
    private val channelId = "i.apps.notifications" // Unique channel ID for notifications
    private val notificationId = 1234 // Unique identifier for the notification
    val firebasePath = "gs://bonfire-d8db1.firebasestorage.app"


    /**
     * Loads profile picture from firebase and updates the ImageView with that image.
     **/
    fun setProfilePicture(context: Context, avatarPath: String, imageView: ImageView){
        if (avatarPath.isEmpty() || (!avatarPath.startsWith("gs://") && !avatarPath.startsWith("http"))) {
            imageView.setImageResource(R.drawable.default_pfp)
            return
        }
        val storage = Firebase.storage
        try{
            val gsReference = storage.getReferenceFromUrl(avatarPath)
            gsReference.downloadUrl.addOnSuccessListener { uri ->
                Log.d(TAG, "avatar uri loaded $uri")
                // Check if context is an Activity and if it is destroyed before calling Glide
                if (context is Activity && (context.isDestroyed || context.isFinishing)) {
                    return@addOnSuccessListener
                }
                // Download directly from StorageReference using Glide
                Glide.with(context).load(uri).placeholder(R.drawable.default_pfp).into(imageView)
            }.addOnFailureListener { e ->
                Log.e(TAG, "Couldn't get avatar uri: $e")
                imageView.setImageResource(R.drawable.default_pfp)
            }
        } catch (e : Exception){
            imageView.setImageResource(R.drawable.default_pfp)
            Log.e(TAG, "Profile picture $avatarPath invalid: $e")
        }
    }

    fun attemptNotification(title:String, contextText:String, context:Context, friendId:String){
        // Request runtime permission for notifications on Android 13 and higher
        val activity = context as? Activity

        // Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity != null) {
            if (ActivityCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
                return
            }
        }

        // If permission is already granted or OS < 13
        sendNotification(title, contextText, context, friendId)
    }

    /**
     * Build and send a notification with a custom layout and action.
     */
    @SuppressLint("MissingPermission")
    fun sendNotification(title:String, contextText:String, context:Context, friendId:String) {
        // Intent that triggers when the notification is tapped
        val intent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        // Passes friendID to chat activity
        intent.putExtra("id", friendId)

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.bonfire_icon) // Notification icon
            .setContentTitle(title) // Title displayed in the notification
            .setContentText(contextText) // Text displayed in the notification
            .setContentIntent(pendingIntent) // Pending intent triggered when tapped
            .setAutoCancel(true) // Dismiss notification when tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Notification priority for better visibility

        // Display the notification
        try {
            with(NotificationManagerCompat.from(context)) {
                notify(
                    notificationId,
                    builder.build()
                )
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification permission", e)
        }
    }

    // Creates list of dictionaries, each containing:
    //      friends ID, name, avatar, and documentPath of private chat with user
    // Then calls createNotificationListeners() with list of dictionaries
    fun listenForNotifs(uid:String, context:Context){
        Log.d(TAG, "listenForNotifs called")
        if (uid.isEmpty()) return

        val db = Firebase.firestore
        val userRef = db.collection("users").document(uid)

        userRef.get()
        .addOnSuccessListener { document ->
            if (document == null || document.data == null) return@addOnSuccessListener

            val userFriends = document.get("friends") as? List<String> ?: return@addOnSuccessListener
            Log.d(TAG, "friends found $userFriends")

            val tasks = mutableListOf<Task<DocumentSnapshot>>()

            // create all friend fetch tasks first
            for (friend in userFriends) {
                val task = db.collection("users").document(friend).get()
                tasks.add(task)
            }

            // wait for ALL to finish
            Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
            .addOnSuccessListener { results ->
                val listOfFriends = mutableListOf<Map<String, String>>()

                for ((index, friendDoc) in results.withIndex()) {
                    val friend = userFriends[index]
                    val friendData = friendDoc.data

                    val chatIdArray = arrayOf(uid, friend)
                    chatIdArray.sort()
                    val chatId = chatIdArray.joinToString("_")

                    listOfFriends.add(
                        mapOf(
                            "documentPath" to "chats/$chatId/messages",
                            "name" to friendData?.get("name").toString(),
                            "friendId" to friend,
                            "friendAvatar" to friendData?.get("avatar").toString()
                        )
                    )
                }

                Log.d(TAG, "created dictionaries of friend data: $listOfFriends")
                createNotificationListeners(listOfFriends, context, uid)
            }
        }
    }

    private fun notifPrefs(context: Context) =
        context.getSharedPreferences("notif_limits", Context.MODE_PRIVATE)

    private fun unopenedKey(friendId: String) = "unopened_$friendId"
    private fun limitEnabledKey(friendId: String) = "limit_enabled_$friendId"
    private val OPEN_CHAT_KEY = "open_chat_friendId"

    fun isLimitEnabled(context: Context, friendId: String): Boolean =
        notifPrefs(context).getBoolean(limitEnabledKey(friendId), false)

    fun getOpenChatFriendId(context: Context): String? =
        notifPrefs(context).getString(OPEN_CHAT_KEY, null)

    fun incrementUnopened(context: Context, friendId: String): Int {
        val p = notifPrefs(context)
        val newVal = p.getInt(unopenedKey(friendId), 0) + 1
        p.edit().putInt(unopenedKey(friendId), newVal).apply()
        return newVal
    }


    fun createNotificationListeners(list:MutableList<Map<String, String>>, context: Context, uid:String){
        // add listener for each DM with a friend, listening for any messages sent
        Log.d(TAG, "createNotificationListeners() called $list")

        var i = 0
        val now = Timestamp.now()
        for (friend in list){
            i += 1
            Log.d(TAG, "adding listener $i to chat with '${friend["name"]}', groupchatId:'${friend["documentPath"]}'")


            Firebase.firestore.collection(friend["documentPath"] ?: "")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                } else if (snapshots == null) {
                    return@addSnapshotListener
                }
                
                for (dc in snapshots.documentChanges) {
                    val messageTimestamp = dc.document.data["timestamp"] as? Timestamp ?: continue

                    // get (or create if it does not exist) app preferences (where bools of whether a friend has been muted is saved)
                    val sharedPref = context.getSharedPreferences("muted", Context.MODE_PRIVATE)
                    val friendNotMuted : Boolean = sharedPref.getInt(dc.document.data["senderId"].toString(), 0) == 0

                    Log.d(TAG, "Received notification from '${friend["name"]}. Friend muted? ${!friendNotMuted}'")
                    if (dc.type == DocumentChange.Type.ADDED
                        && messageTimestamp > now
                        && dc.document.data["senderId"] != uid
                        && friendNotMuted) {

                        val friendId = friend["friendId"].toString()

                        // If user currently has this chat open, don’t notify (and don’t count it as unopened)
                        if (getOpenChatFriendId(context) == friendId) {
                            break
                        }

                        // If limiting is OFF for this friend: behave normally
                        if (!isLimitEnabled(context, friendId)) {
                            attemptNotification(
                                friend["name"].toString(),
                                dc.document.data["text"].toString(),
                                context,
                                friendId
                            )
                            break
                        }

                        // Limiting is ON: increment unopened count and suppress after 3
                        val unopenedCount = incrementUnopened(context, friendId)
                        if (unopenedCount <= 6) {
                            attemptNotification(
                                friend["name"].toString(),
                                dc.document.data["text"].toString(),
                                context,
                                friendId
                            )
                            break
                        } else {
                            Log.d(TAG, "Suppressed notif for $friendId (unopenedCount=$unopenedCount > 3)")
                        }
                    }
                    break
                }
            }
        }
    }

    /**
     * Sets navigation listeners for the bottom navigation button to quickly move to specific pages.
     *
     * @param currentPage context from which to make calls and insert functionality
     */
    public fun defineBottomNavButtons(currentPage : AppCompatActivity) {
        //if statements make sure not instantiating new page that is the current page
        if (currentPage !is GroupChatListActivity) {
            /**
             * Sets navigation for the chat button in the bottom navigation.
             */
            val chatButton: ImageButton = currentPage.findViewById(R.id.menu_button_chat)
            chatButton.setOnClickListener {
                val intent = Intent(currentPage, GroupChatListActivity::class.java)
                currentPage.startActivity(intent)
                currentPage.finish()
            }
        }

        if (currentPage !is FriendAddActivity){
            /**
             * Sets navigation for the friend button in the bottom navigation.
             */
            val friendButton: ImageButton = currentPage.findViewById(R.id.menu_button_friends)
            friendButton.setOnClickListener {
                val intent = Intent(currentPage, FriendAddActivity::class.java)
                currentPage.startActivity(intent)
                currentPage.finish()
            }
        }

        if (currentPage !is AccountActivity) {
            /**
             * Sets navigation for the account button in the bottom navigation.
             */
            val accountButton: ImageButton = currentPage.findViewById(R.id.menu_button_account)
            accountButton.setOnClickListener {
                val intent = Intent(currentPage, AccountActivity::class.java)
                currentPage.startActivity(intent)
                currentPage.finish()
            }
        }

        /**
         * Sets navigation for the logout button in the bottom navigation.
         */
        val logoutButton: ImageButton = currentPage.findViewById(R.id.menu_button_logout)
        logoutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(currentPage, WelcomeActivity::class.java)
            currentPage.startActivity(intent)
            currentPage.finish()
        }
    }

    fun makeToast(context: Context, text:String){
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
