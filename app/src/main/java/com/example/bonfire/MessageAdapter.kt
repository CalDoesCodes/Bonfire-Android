package com.example.bonfire

import android.media.MediaPlayer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.storage.storage
import java.text.SimpleDateFormat
import java.util.Date


// RecyclerView adapter for the scrollable messages view
class MessageAdapter(private val data: ArrayList<Map<String, Any>?>, val inPrivateChat: Boolean, val uid:String) : RecyclerView.Adapter<MessageAdapter.ItemViewHolder>() {
    private var currentPlayingPosition = -1
    private var isPaused = false
    val helper = Helper()
    private val TAG = "Message Adapter"
    var mRecyclerView: RecyclerView? = null

    // Akin to onCreate method to initialize each instance (each message)
    inner class ItemViewHolder(view: View): RecyclerView.ViewHolder(view){
        val displayNameTextView: TextView = view.findViewById(R.id.message_user)
        val avatarURLTextView: ImageView = view.findViewById(R.id.message_profile)
        val textTextView: TextView = view.findViewById(R.id.message_text)
        val timestampTextView: TextView = view.findViewById(R.id.message_timestamp)
        val checkReadImageView: ImageView = view.findViewById(R.id.check_read)
        val messageImageView: ImageView = view.findViewById(R.id.message_image)
        val voiceMessageCard: CardView = view.findViewById(R.id.voiceMessageCard)
        val voiceMessageImageButton: ImageButton = view.findViewById(R.id.voiceMessageImageButton)
        val mediaPlayer = MediaPlayer()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        mRecyclerView = recyclerView
    }

    // Define each entry's layout/look
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val inflatedView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.message_layout, parent, false)
        return ItemViewHolder(inflatedView)
    }

    //Set values to the views based on the position of the recyclerView
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val message : Map<String, Any>? = data[position]
        holder.displayNameTextView.text = (message?.get("displayName") ?: "Anonymous").toString()
        holder.textTextView.text = message?.get("text")?.toString()
        holder.timestampTextView.text = formatTimestampToString(message?.get("timestamp") as Timestamp)
        helper.setProfilePicture(mRecyclerView!!.context, (message["imageUrl"] ?: "") as String, holder.messageImageView)
        helper.setProfilePicture(mRecyclerView!!.context, (message["photoURL"] ?: "") as String, holder.avatarURLTextView)

        // If no attached text in message, hide text
        holder.textTextView.isGone = message["text"] == null || message["text"] == ""

        // If no attached image in message, hide imageView
        holder.messageImageView.isGone = message["imageUrl"] == null

        // If voice message in message is null, hide imageView
        holder.voiceMessageCard.isGone = message["audioUrl"] == null

        // else initialize voice message
        if (message["audioUrl"] != null){
            holder.voiceMessageCard.isGone = false

            holder.voiceMessageImageButton.setOnClickListener {
                val voicePath = message["audioUrl"] as String
                handleVoicePlayback(holder, position, voicePath)
            }
            holder.mediaPlayer.setOnCompletionListener {
                holder.voiceMessageImageButton.setImageResource(R.drawable.microphone)
            }
        }

        // Only display read marks in DMs
        // if most recent show check mark (sent) or double check mark (read)
        if(inPrivateChat && position == itemCount - 1 && (message["senderId"] == uid)){
            val messageRead = message["read"] as Boolean
            val checkReadImageViewId = if (messageRead) R.drawable.double_check else R.drawable.check
            holder.checkReadImageView.setImageResource(checkReadImageViewId)
            holder.checkReadImageView.visibility = View.VISIBLE
        }
    }

    private fun handleVoicePlayback(holder: ItemViewHolder, position: Int, voiceUrl: String) {
        val mediaPlayer = holder.mediaPlayer

        // User clicked the message that is already active
        if (currentPlayingPosition == position) {
            if (mediaPlayer.isPlaying) {
                // It was playing -> Pause it
                mediaPlayer.pause()
                isPaused = true
                holder.voiceMessageImageButton.setImageResource(R.drawable.play)
            } else if (isPaused) {
                // It was paused -> Resume it
                mediaPlayer.start()
                isPaused = false
                holder.voiceMessageImageButton.setImageResource(R.drawable.pause)
            }
        }
        // User clicked a new message (or first time playing)
        else {
            prepareAndPlay(holder, position, voiceUrl)
        }
    }

    private fun prepareAndPlay(holder: ItemViewHolder, position: Int, voiceUrl: String) {
        val storage = Firebase.storage
        val gsReference = storage.getReferenceFromUrl(voiceUrl)

        gsReference.downloadUrl.addOnSuccessListener { uri ->
            holder.mediaPlayer.apply {
                reset()
                setDataSource(uri.toString())
                prepareAsync()

                setOnPreparedListener {
                    start()
                    currentPlayingPosition = position
                    isPaused = false
                    holder.voiceMessageImageButton.setImageResource(R.drawable.pause)
                }

                setOnCompletionListener {
                    holder.voiceMessageImageButton.setImageResource(R.drawable.play)
                    currentPlayingPosition = -1
                    isPaused = false
                }
            }
        }
    }

    fun formatTimestampToString(timestamp: Timestamp): String{
        val timestampDate:Date = timestamp.toDate()
        val dateFormat = SimpleDateFormat("hh:mm a")
        return dateFormat.format(timestampDate)
    }

    //  Total number of elements in recyclerView
    override fun getItemCount(): Int {
        return data.size
    }
}