package com.example.bonfire

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class SignInActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.signin_layout)

        auth = Firebase.auth

        val signInButton: Button = findViewById(R.id.signin_button)
        signInButton.setOnClickListener {
            // get contents of email/user and password inputs
            val emailEditText:  TextInputEditText = findViewById(R.id.signin_email_edit)
            val passwordEditText: TextInputEditText = findViewById(R.id.signin_password_edit)
            val emailEditable = emailEditText.getText()
            val passwordEditable = passwordEditText.getText()

            signIn(emailEditable, passwordEditable)
        }

        // switch to sign in activity
        val switchButton: Button = findViewById(R.id.signin_switch_button)
        switchButton.setOnClickListener {
            val intent = Intent(this, SignUpActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    fun signIn(email: Editable?, password:Editable?){
        val email = email.toString()
        val password = password.toString()

        if (email == "" || password == ""){
            // pop alert if not all fields filled
            Toast.makeText(baseContext, "Please fill out all fields", Toast.LENGTH_SHORT).show()
        }
        else{
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d(TAG, "signInWithEmail:success")
                        val intent = Intent(this, GroupChatListActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w(TAG, "signInWithEmail:failure", task.exception)
                        Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                        //updateUI(null)
                    }
                }
        }
    }
    companion object {
        private const val TAG = "EmailPassword"
    }
}