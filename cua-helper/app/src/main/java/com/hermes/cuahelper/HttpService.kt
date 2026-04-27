package com.hermes.cuahelper

import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

class HttpService : Service() {

    private var server: NanoHTTPD? = null
    private val PORT = 8640

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hermes CUA")
            .setContentText("运行中 · :$PORT")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        if (server == null) {
            server = object : NanoHTTPD(PORT) {
                override fun serve(session: IHTTPSession): Response {
                    return try {
                        handleRequest(session)
                    } catch (e: Exception) {
                        val error = JSONObject()
                        error.put("error", e.message ?: "Unknown error")
                        error.put("type", e.javaClass.simpleName)
                        newFixedLengthResponse(Status.INTERNAL_ERROR, "application/json", error.toString())
                    }
                }
            }
            try {
                server?.start()
            } catch (e: Exception) {
                // Server failed to start
            }
        }

        return START_STICKY
    }

    private fun handleRequest(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method

        return when {
            uri == "/" || uri == "/status" -> handleStatus()
            uri == "/dump" -> handleDump()
            uri == "/click" -> handleClick(session)
            uri == "/swipe" -> handleSwipe(session)
            uri == "/back" -> handleBack()
            uri == "/home" -> handleHome()
            uri == "/text" -> handleText(session)
            uri == "/screenshot" -> handleScreenshot()
            else -> jsonResponse(Status.NOT_FOUND, JSONObject().apply {
                put("error", "not_found")
                put("path", uri)
            })
        }
    }

    private fun handleStatus(): Response {
        val json = JSONObject()
        json.put("status", "running")
        json.put("port", PORT)
        json.put("has_accessibility", MyAccessibilityService.instance != null)
        json.put("has_window", MyAccessibilityService.getRootNode() != null)
        return jsonResponse(Status.OK, json)
    }

    private fun handleDump(): Response {
        val root = try {
            MyAccessibilityService.getRootNode()
        } catch (e: Exception) {
            null
        }
        if (root == null) {
            return jsonResponse(Status.OK, JSONObject().apply {
                put("status", "no_window")
                put("error", "No active window or accessibility not connected")
                put("has_accessibility", MyAccessibilityService.instance != null)
            })
        }
        return try {
            val xml = dumpNodeToXml(root, 0)
            root.recycle()
            newFixedLengthResponse(Status.OK, "application/xml", xml)
        } catch (e: Exception) {
            jsonResponse(Status.INTERNAL_ERROR, JSONObject().apply {
                put("error", "dump failed: ${e.message}")
            })
        }
    }

    private fun handleClick(session: IHTTPSession): Response {
        val x = session.parameters["x"]?.firstOrNull()?.toIntOrNull() ?: return jsonResponse(Status.BAD_REQUEST, JSONObject().apply {
            put("error", "missing x parameter")
        })
        val y = session.parameters["y"]?.firstOrNull()?.toIntOrNull() ?: return jsonResponse(Status.BAD_REQUEST, JSONObject().apply {
            put("error", "missing y parameter")
        })

        return try {
            val service = MyAccessibilityService.instance ?: return jsonResponse(Status.SERVICE_UNAVAILABLE, JSONObject().apply {
                put("error", "accessibility service not connected")
            })

            val path = Path()
            path.moveTo(x.toFloat(), y.toFloat())
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                .build()
            service.dispatchGesture(gesture, null, null)

            jsonResponse(Status.OK, JSONObject().apply {
                put("status", "clicked")
                put("x", x)
                put("y", y)
            })
        } catch (e: Exception) {
            jsonResponse(Status.INTERNAL_ERROR, JSONObject().apply {
                put("error", "click failed: ${e.message}")
            })
        }
    }

    private fun handleSwipe(session: IHTTPSession): Response {
        val x1 = session.parameters["x1"]?.firstOrNull()?.toIntOrNull() ?: return jsonResponse(Status.BAD_REQUEST, JSONObject().apply {
            put("error", "missing x1")
        })
        val y1 = session.parameters["y1"]?.firstOrNull()?.toIntOrNull() ?: return jsonResponse(Status.BAD_REQUEST, JSONObject().apply {
            put("error", "missing y1")
        })
        val x2 = session.parameters["x2"]?.firstOrNull()?.toIntOrNull() ?: return jsonResponse(Status.BAD_REQUEST, JSONObject().apply {
            put("error", "missing x2")
        })
        val y2 = session.parameters["y2"]?.firstOrNull()?.toIntOrNull() ?: return jsonResponse(Status.BAD_REQUEST, JSONObject().apply {
            put("error", "missing y2")
        })

        return try {
            val service = MyAccessibilityService.instance ?: return jsonResponse(Status.SERVICE_UNAVAILABLE, JSONObject().apply {
                put("error", "accessibility service not connected")
            })

            val path = Path()
            path.moveTo(x1.toFloat(), y1.toFloat())
            path.lineTo(x2.toFloat(), y2.toFloat())
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
                .build()
            service.dispatchGesture(gesture, null, null)

            jsonResponse(Status.OK, JSONObject().apply {
                put("status", "swiped")
            })
        } catch (e: Exception) {
            jsonResponse(Status.INTERNAL_ERROR, JSONObject().apply {
                put("error", "swipe failed: ${e.message}")
            })
        }
    }

    private fun handleBack(): Response {
        return try {
            val service = MyAccessibilityService.instance ?: return jsonResponse(Status.SERVICE_UNAVAILABLE, JSONObject().apply {
                put("error", "accessibility service not connected")
            })
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            jsonResponse(Status.OK, JSONObject().apply { put("status", "back") })
        } catch (e: Exception) {
            jsonResponse(Status.INTERNAL_ERROR, JSONObject().apply { put("error", e.message) })
        }
    }

    private fun handleHome(): Response {
        return try {
            val service = MyAccessibilityService.instance ?: return jsonResponse(Status.SERVICE_UNAVAILABLE, JSONObject().apply {
                put("error", "accessibility service not connected")
            })
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            jsonResponse(Status.OK, JSONObject().apply { put("status", "home") })
        } catch (e: Exception) {
            jsonResponse(Status.INTERNAL_ERROR, JSONObject().apply { put("error", e.message) })
        }
    }

    private fun handleText(session: IHTTPSession): Response {
        val text = session.parameters["text"]?.firstOrNull() ?: return jsonResponse(Status.BAD_REQUEST, JSONObject().apply {
            put("error", "missing text parameter")
        })
        // Find a focused node or editable text field and set text
        return try {
            val root = MyAccessibilityService.getRootNode()
            if (root == null) {
                return jsonResponse(Status.OK, JSONObject().apply {
                    put("status", "no_window")
                    put("message", "Copy this to clipboard: $text")
                })
            }
            val result = findAndSetText(root, text)
            root.recycle()
            jsonResponse(Status.OK, JSONObject().apply {
                put("status", if (result) "text_set" else "no_editable_field_found")
                put("text", text)
            })
        } catch (e: Exception) {
            jsonResponse(Status.INTERNAL_ERROR, JSONObject().apply {
                put("error", "text failed: ${e.message}")
                put("text", text)
            })
        }
    }

    private fun handleScreenshot(): Response {
        return jsonResponse(Status.OK, JSONObject().apply {
            put("status", "not_implemented")
            put("message", "Screenshot requires MediaProjection API which is not yet implemented")
        })
    }

    // Utility: dump node tree to XML
    private fun dumpNodeToXml(node: AccessibilityNodeInfo, indent: Int): String {
        val sb = StringBuilder()
        val padding = "  ".repeat(indent)
        sb.append("$padding<node")
        appendAttr(sb, "class", node.className?.toString())
        appendAttr(sb, "text", node.text?.toString())
        appendAttr(sb, "id", node.viewIdResourceName?.toString())

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        sb.append(" bounds=\"[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]\"")
        appendAttr(sb, "clickable", if (node.isClickable) "true" else "false")
        appendAttr(sb, "scrollable", if (node.isScrollable) "true" else "false")
        appendAttr(sb, "checked", if (node.isChecked) "true" else "false")
        appendAttr(sb, "enabled", if (node.isEnabled) "true" else "false")

        val childCount = try { node.childCount } catch (e: Exception) { 0 }
        if (childCount > 0) {
            sb.append(">\n")
            for (i in 0 until childCount) {
                try {
                    val child = node.getChild(i)
                    if (child != null) {
                        sb.append(dumpNodeToXml(child, indent + 1))
                        child.recycle()
                    }
                } catch (e: Exception) {
                    // skip problematic children
                }
            }
            sb.append("$padding</node>\n")
        } else {
            sb.append(" />\n")
        }
        return sb.toString()
    }

    private fun appendAttr(sb: StringBuilder, name: String, value: String?) {
        if (value != null && value.isNotBlank()) {
            val escaped = value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
            sb.append(" $name=\"$escaped\"")
        }
    }

    private fun findAndSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.isEditable) {
            try {
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                })
                return true
            } catch (e: Exception) {
                return false
            }
        }
        val childCount = try { node.childCount } catch (e: Exception) { 0 }
        for (i in 0 until childCount) {
            try {
                val child = node.getChild(i)
                if (child != null) {
                    if (findAndSetText(child, text)) {
                        child.recycle()
                        return true
                    }
                    child.recycle()
                }
            } catch (e: Exception) {
                // skip
            }
        }
        return false
    }

    private fun jsonResponse(status: Status, json: JSONObject): Response {
        return newFixedLengthResponse(status, "application/json", json.toString())
    }

    override fun onDestroy() {
        try { server?.stop() } catch (e: Exception) {}
        server = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hermes CUA Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "hermes_cua_channel"
    }
}
