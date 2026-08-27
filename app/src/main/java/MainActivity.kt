package com.pixelassistant.chess

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var usernameInput: EditText
    private lateinit var saveUsernameBtn: Button
    private lateinit var accessibilityStatus: TextView
    private lateinit var enableAccessibilityBtn: Button
    private lateinit var toggleFeaturesBtn: Button
    private lateinit var startBtn: Button

    private var showArrows = true
    private var showEvalOnTap = true
    private var autoRematch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences("ChessAssistantPrefs", Context.MODE_PRIVATE)

        usernameInput = findViewById(R.id.usernameInput)
        saveUsernameBtn = findViewById(R.id.saveUsernameBtn)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        enableAccessibilityBtn = findViewById(R.id.enableAccessibilityBtn)
        toggleFeaturesBtn = findViewById(R.id.toggleFeaturesBtn)
        startBtn = findViewById(R.id.startBtn)

        val savedUsername = sharedPrefs.getString("username", "")
        if (!savedUsername.isNullOrEmpty()) {
            usernameInput.setText(savedUsername)
            usernameInput.isEnabled = false
            saveUsernameBtn.text = "Saved ✓"
            saveUsernameBtn.isEnabled = false
        }

        saveUsernameBtn.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            if (username.isEmpty()) {
                Toast.makeText(this, "Enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sharedPrefs.edit().putString("username", username).apply()
            usernameInput.isEnabled = false
            saveUsernameBtn.text = "Saved ✓"
            saveUsernameBtn.isEnabled = false
            Toast.makeText(this, "Username saved!", Toast.LENGTH_SHORT).show()
            updateUI()
        }

        enableAccessibilityBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        toggleFeaturesBtn.setOnClickListener {
            showFeatureToggleDialog()
        }

        startBtn.setOnClickListener {
            startOverlayAndLaunchChess()
        }

        updateUI()
    }

    private fun updateUI() {
        val accessibilityEnabled = isAccessibilityEnabled()
        if (accessibilityEnabled) {
            accessibilityStatus.text = "✅ Accessibility: ON"
            accessibilityStatus.setTextColor(0xFF00FF00.toInt())
        } else {
            accessibilityStatus.text = "❌ Accessibility: OFF"
            accessibilityStatus.setTextColor(0xFFFF4444.toInt())
        }

        val username = sharedPrefs.getString("username", "")
        if (username.isNullOrEmpty() || !isAccessibilityEnabled()) {
            toggleFeaturesBtn.isEnabled = false
            startBtn.isEnabled = false
        } else {
            toggleFeaturesBtn.isEnabled = true
            startBtn.isEnabled = true
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "com.pixelassistant.chess/com.pixelassistant.chess.ChessService"
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            enabledServices?.contains(service) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun showFeatureToggleDialog() {
        val checkedItems = booleanArrayOf(showArrows, showEvalOnTap, autoRematch)
        val items = arrayOf("Show Arrows", "Show Eval on Tap", "Auto-Rematch")

        AlertDialog.Builder(this)
            .setTitle("Toggle Features")
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                when (which) {
                    0 -> showArrows = isChecked
                    1 -> showEvalOnTap = isChecked
                    2 -> autoRematch = isChecked
                }
            }
            .setPositiveButton("Save") { _, _ ->
                sharedPrefs.edit().putBoolean("showArrows", showArrows).apply()
                sharedPrefs.edit().putBoolean("showEvalOnTap", showEvalOnTap).apply()
                sharedPrefs.edit().putBoolean("autoRematch", autoRematch).apply()
                Toast.makeText(this, "Features saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startOverlayAndLaunchChess() {
        showArrows = sharedPrefs.getBoolean("showArrows", true)
        showEvalOnTap = sharedPrefs.getBoolean("showEvalOnTap", true)
        autoRematch = sharedPrefs.getBoolean("autoRematch", true)

        val serviceIntent = Intent(this, ChessService::class.java)
        serviceIntent.putExtra("showArrows", showArrows)
        serviceIntent.putExtra("showEvalOnTap", showEvalOnTap)
        serviceIntent.putExtra("autoRematch", autoRematch)
        startService(serviceIntent)

        try {
            val chessIntent = packageManager.getLaunchIntentForPackage("com.chess")
            if (chessIntent != null) {
                startActivity(chessIntent)
            } else {
                Toast.makeText(this, "Opening Chess.com website", Toast.LENGTH_LONG).show()
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chess.com"))
                startActivity(browserIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening Chess.com", Toast.LENGTH_SHORT).show()
        }

        moveTaskToBack(true)
    }

    fun openYouTube(view: View) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com/@pixeld3v"))
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
