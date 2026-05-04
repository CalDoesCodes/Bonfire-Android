package com.example.bonfire

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import java.util.Calendar


class SignUpActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    lateinit var TAG:String
    val helper = Helper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signup_layout)

        val db = Firebase.firestore
        auth = Firebase.auth
        TAG = "signup"

        val birthdateEditText: TextInputEditText = findViewById(R.id.signup_birthdate_edit)
        birthdateEditText.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val dateString = "${selectedYear}-${selectedMonth + 1}-${selectedDay}"
                birthdateEditText.setText(dateString)
            }, year, month, day)
            datePickerDialog.show()
        }

        // Button to switch to main screen activity
        val signupButton: Button = findViewById(R.id.signup_button)
        signupButton.setOnClickListener {
            // get contents of email, user and password inputs
            val userEditText: TextInputEditText = findViewById(R.id.signup_username_edit)
            val usernameString = userEditText.getText().toString()

            val emailEditText: TextInputEditText = findViewById(R.id.signup_email_edit)
            val emailString = emailEditText.getText().toString()

            val passwordEditText: TextInputEditText = findViewById(R.id.signup_password_edit)
            val passwordString = passwordEditText.getText().toString()
            
            val birthdateString = birthdateEditText.getText().toString()

            signUp(auth, db, usernameString, emailString, passwordString, birthdateString)
        }

        // switch to sign in activity
        val switchButton: Button = findViewById(R.id.signup_switch_button)
        switchButton.setOnClickListener {
            val intent = Intent(this, SignInActivity::class.java)
            startActivity(intent)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    fun makeToast(string:String){
        Toast.makeText(baseContext, string, Toast.LENGTH_SHORT).show()
    }

    fun signUp (auth: FirebaseAuth, db: FirebaseFirestore, username:String, email:String, password:String, birthdate: String) {
        if (username == "" || email == "" || password == "" || birthdate == "") {
            // pop alert if not all fields filled
            makeToast("Please fill out all fields")
            return
        }
        makeAccount(auth, db, username, email, password, birthdate)
    }

    fun makeAccount(auth: FirebaseAuth, db: FirebaseFirestore, username:String, email:String, password:String, birthdate: String){
        // Attempt to create user
        auth.createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                // sign in with just created account
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "signInWithEmail:success")

                            // add data to users/[uid]/
                            val uid: String? = auth.currentUser?.uid
                            val avatarPath = helper.firebasePath + "/Profile_Pictures/logo.png"

                            // I'm sorry for how nested and awful this function is
                            val storage = Firebase.storage
                            try{
                                // Get URI of default profile picture
                                val gsReference = storage.getReferenceFromUrl(avatarPath)
                                gsReference.downloadUrl.addOnSuccessListener { uri ->
                                    val data = hashMapOf(
                                        "avatar" to uri,
                                        "createdAt" to Timestamp.now(),
                                        "bio" to "Welcome to Bonfire!",
                                        "email" to email,
                                        "name" to username,
                                        "friends" to arrayOf("ps3Q2NASt3hTeb2b5cJ8"),
                                        "displayName" to username,
                                        "birthdate" to birthdate
                                    )
                                    db.collection("users").document(uid.toString()).set(data)

                                    val intent = Intent(this, GroupChatListActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }.addOnFailureListener { e ->
                                    Log.e(TAG, "Couldn't get avatar uri: $e")
                                }
                            } catch (e : IllegalArgumentException){
                                Log.e(TAG, "Profile picture $avatarPath not found: $e")
                            }

                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithEmail:failure", task.exception)
                            Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                // If sign up fails, display a message to the user.
                Log.w(TAG, "createUserWithEmail:failure", task.exception)
                makeToast(getFriendlyErrorMessage(task.exception))
            }
        }
    }

    fun getFriendlyErrorMessage(error: Exception?):String {
        return when (error) {
            is FirebaseAuthInvalidCredentialsException -> "Please enter a valid email address."
            is FirebaseException -> "Password does not meet requirements."
            else -> "An unexpected error occurred. Please try again."
        }
//            "auth/user-not-found" -> "Invalid password. Please try again."
//            "auth/wrong-password" -> "Invalid password. Please try again."
//            "auth/email-already-in-use" ->"An account with this email already exists."
    }
}
