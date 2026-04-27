package com.hermes.cuahelper

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var startBtn: Button
    private lateinit var stopBtn: Button
    private lateinit var accessibilityBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startBtn = findViewById(R.id.startBtn)
        stopBtn = findViewById(R.id.stopBtn)
        accessibilityBtn = findViewById(R.id.accessibilityBtn)

        startBtn.setOnClickListener { startHttpService() }
        stopBtn.setOnClickListener { stopHttpService() }
        accessibilityBtn.setOnClickListener { openAccessibilitySettings() }

        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun startHttpService() {
        val intent = Intent(this, HttpService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateStatus()
    }

    private fun stopHttpService() {
        val intent = Intent(this, HttpService::class.java)
        stopService(intent)
        updateStatus()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun updateStatus() {
        val isRunning = MyAccessibilityService.instance != null
        val hasAccessibility = MyAccessibilityService.instance != null

        if (isRunning) {
            statusText.text = "✅ 服务运行中\n端口: 8640"
            statusText.setTextColor(0xFF4CAF50.toInt())
            startBtn.isEnabled = false
            stopBtn.isEnabled = true
        } else {
            statusText.text = if (hasAccessibility) {
                "⚠️ 服务未运行"
            } else {
                "❌ 请先启用无障碍服务"
            }
            statusText.setTextColor(0xFFFF5722.toInt())
            startBtn.isEnabled = true
            stopBtn.isEnabled = false
        }
    }
}
