package com.example.bonfire

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

// RecyclerView adapter for the scrollable messages view
class MessageAdapter(private val data: ArrayList<Map<String, Any>?>, val inPrivateChat: Boolean, val uid:String) : RecyclerView.Adapter<MessageAdapter.ItemViewHolder>() {
    val helper = Helper()
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

        // photoURL is avatar url, imageUrl is the attached image
        // If no attached image in message, hide imageView
        if (message["text"] == null || message["text"] == ""){
            holder.textTextView.isGone = true;
        }
        if (message["imageUrl"] == null){
            holder.messageImageView.isGone = true;
        }
        if (message["voiceURL"] == null){
            holder.voiceMessageCard.isGone = true;
        } else{
            holder.voiceMessageImageButton.setOnClickListener {
                helper.playVoiceMessage(message["voiceURL"] as String)
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