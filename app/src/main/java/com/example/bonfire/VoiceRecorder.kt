package com.example.bonfire

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Button
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.ImageButton
import android.widget.TextView
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import java.util.UUID
import androidx.core.net.toUri
import java.io.File

/**
 * Popup for recording a voice message.
 */
internal class VoiceRecorder {
    private var startTime: Long = 0
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var audioPath: String = ""
    private var isRecording = false
    private var isPlaying = false

    @SuppressLint("SetTextI18n")
    fun openVoiceRecorderDialog(context: Context) {
        val activity = context as Activity

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                200
            )
            return
        }

        val dialogView = activity.layoutInflater.inflate(R.layout.voice_record_dialog, null)

        val builder = AlertDialog.Builder(context)
        val dialog = builder.create()
        dialog.setView(dialogView)
        dialog.show()

        audioPath = "${context.cacheDir}/voice_message.webm"

        val recordBtn = dialogView.findViewById<Button>(R.id.recordBtn)
        val messageLength = dialogView.findViewById<TextView>(R.id.messageLength)
        recordBtn.setOnClickListener {
            isRecording = !isRecording
            if (isRecording){
                recordBtn.text = "Stop"
                startRecording()

            } else{
                recordBtn.text = "Record"
                val secondsInt = stopRecording()
                val minutes = secondsInt / 60
                val seconds = "%02d".format(secondsInt % 60)
                messageLength.text = "$minutes:$seconds"
            }
        }

        val playBtn = dialogView.findViewById<Button>(R.id.playBtn)
        playBtn.setOnClickListener {
            isPlaying = !isPlaying
            if (!isRecording){
                if (isPlaying){
                    playBtn.text = "Stop"
                    playRecording(playBtn)
                } else{
                    playBtn.text = "Play"
                    pauseRecording()
                }
            }
        }

        val sendBtn = dialogView.findViewById<Button>(R.id.sendBtn)
        sendBtn.setOnClickListener {
            sendVoiceMessage(context as ChatActivity)
            dialog.dismiss()
        }

        val closeBtn = dialogView.findViewById<ImageButton>(R.id.voiceMessageClose)
        closeBtn.setOnClickListener {
            dialog.cancel()
        }

        dialog.show()
    }

    private fun startRecording() {
        startTime = System.currentTimeMillis()
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.WEBM)
            setOutputFile(audioPath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            }

            prepare()
            start()
        }
    }

    // Returns length of recording in seconds
    private fun stopRecording() : Int {
        val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        recorder?.apply {
            stop()
            release()
        }
        recorder = null
        return duration
    }

    private fun playRecording(playBtn: Button) {
        // If player exists and is paused, just resume
        if (player != null && !isPlaying) {
            player?.start()
            isPlaying = true
            return
        }

        // Otherwise, create new player
        player = MediaPlayer().apply {
            setDataSource(audioPath)
            prepare()
            start()
            setOnCompletionListener {
                playBtn.text = "Play"
                release()
                player = null
            }
        }
    }

    private fun pauseRecording() {
        if (player?.isPlaying == true) {
            player?.pause()
            isPlaying = false
        }
    }

    fun sendVoiceMessage(context: ChatActivity) {
        val storageRef = Firebase.storage.reference
        val fileName = UUID.randomUUID().toString()
        val voiceRef = storageRef.child("Voice_Messages/$fileName")
        val file = File(audioPath)
        val uri = Uri.fromFile(file)
        voiceRef.putFile(uri).addOnSuccessListener {
            voiceRef.downloadUrl.addOnSuccessListener { downloadUri ->
                context.sendVoiceMessage(downloadUri.toString())
            }
        }
    }
}