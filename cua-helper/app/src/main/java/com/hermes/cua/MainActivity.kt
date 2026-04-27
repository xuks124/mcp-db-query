package com.hermes.cua

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var btnAccessibility: Button
    private lateinit var btnMediaProjection: Button
    private lateinit var btnStart: Button

    companion object {
        const val MEDIA_PROJECTION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnMediaProjection = findViewById(R.id.btnMediaProjection)
        btnStart = findViewById(R.id.btnStart)

        btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        btnMediaProjection.setOnClickListener {
            requestMediaProjection()
        }

        btnStart.setOnClickListener {
            startCuaService()
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val sb = StringBuilder()
        sb.appendLine("📱 Hermes CUA 助手")

        val accessibilityEnabled = isAccessibilityServiceEnabled()
        sb.appendLine("无障碍服务: ${if (accessibilityEnabled) "✅ 已启用" else "❌ 未启用"}")

        sb.appendLine("MediaProjection: ${if (CuaService.mediaProjection != null) "✅ 已授权" else "❌ 未授权"}")

        sb.appendLine("服务运行: ${if (CuaService.isRunning) "✅ 运行中" else "❌ 未启动"}")
        sb.appendLine("HTTP 端口: 8765")

        if (CuaService.isRunning) {
            sb.appendLine("\nTermux 连接命令:")
            sb.appendLine("  curl http://127.0.0.1:8765/status")
        }

        statusText.text = sb.toString()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/.CuaAccessibilityService"
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            return enabledServices?.contains(service) == true
        } catch (e: Exception) {
            return false
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "请找到并启用「Hermes CUA 助手」", Toast.LENGTH_LONG).show()
    }

    private fun requestMediaProjection() {
        val intent = startMediaProjectionIntent()
        startActivityForResult(intent, MEDIA_PROJECTION_REQUEST_CODE)
    }

    private fun startMediaProjectionIntent(): Intent {
        val service = getSystemService(android.app.MediaProjectionManager::class.java)
        return service.createScreenCaptureIntent()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            CuaService.mediaProjectionResult = Pair(resultCode, data)
            Toast.makeText(this, "MediaProjection 授权成功", Toast.LENGTH_SHORT).show()
            updateStatus()
        }
    }

    private fun startCuaService() {
        if (!isAccessibilityServiceEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("需要无障碍服务")
                .setMessage("请先启用无障碍服务，否则无法执行点击、滑动等操作。")
                .setPositiveButton("去设置") { _, _ -> openAccessibilitySettings() }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        val intent = Intent(this, CuaService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "服务已启动，监听 127.0.0.1:8765", Toast.LENGTH_SHORT).show()
        updateStatus()
    }
}
