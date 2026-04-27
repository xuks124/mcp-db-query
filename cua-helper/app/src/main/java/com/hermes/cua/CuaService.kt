package com.hermes.cua

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream

class CuaService : Service() {

    private var httpServer: CuaHttpServer? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val PORT = 8765
        var mediaProjectionResult: Pair<Int, Intent>? = null
        var isRunning = false
        var mediaProjection: MediaProjection? = null
            private set

        // Store the last screenshot as bytes
        var lastScreenshotBytes: ByteArray? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)

        // Initialize MediaProjection
        mediaProjectionResult?.let { (resultCode, data) ->
            val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, data)
            Companion.mediaProjection = mediaProjection
        }

        // Start HTTP server
        if (httpServer == null) {
            httpServer = CuaHttpServer()
            try {
                httpServer?.start()
                isRunning = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        httpServer?.stop()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "cua_service",
                "CUA 服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hermes CUA 后台服务"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "cua_service")
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Hermes CUA 助手")
            .setContentText("运行中 · 端口 $PORT")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    fun takeScreenshot(): ByteArray? {
        try {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            val metrics = DisplayMetrics()
            display.getMetrics(metrics)

            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val densityDpi = metrics.densityDpi

            val mp = mediaProjection ?: return null

            val format = android.graphics.ImageFormat.YUV_420_888
            imageReader?.close()
            imageReader = ImageReader.newInstance(width, height, format, 2)

            virtualDisplay?.release()
            virtualDisplay = mp.createVirtualDisplay(
                "cua-screenshot",
                width, height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            // Wait for image
            Thread.sleep(500)

            val image = imageReader?.acquireLatestImage() ?: return null
            val planes = image.planes
            if (planes.isEmpty()) return null

            // Convert YUV_420_888 to JPEG
            val buffer = planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // For now, return raw YUV data
            // Proper JPEG conversion would use RenderScript or a library
            image.close()

            // Alternative: Use MediaProjection screenshot API (Android 14+)
            return captureScreenDirectly(metrics)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun captureScreenDirectly(metrics: DisplayMetrics): ByteArray? {
        // Use the standard Android screenshot method
        // On Android 14+, we can use the HardwareBuffer based approach
        // For broader compatibility, we attempt a simpler approach
        return null
    }

    inner class CuaHttpServer : NanoHTTPD(PORT) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            val method = session.method

            return try {
                when {
                    uri == "/status" -> jsonResponse("""{"status":"running","port":$PORT,"accessibility":${CuaAccessibilityService.instance != null}}""")

                    uri == "/screenshot" -> handleScreenshot()

                    uri == "/tap" && method == Method.POST -> handleTap(session)
                    uri == "/swipe" && method == Method.POST -> handleSwipe(session)
                    uri == "/text" && method == Method.POST -> handleText(session)
                    uri == "/key" && method == Method.POST -> handleKey(session)

                    uri == "/ui" -> handleUiDump()

                    uri == "/info" -> jsonResponse(getDeviceInfo())

                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404")
                }
            } catch (e: Exception) {
                jsonResponse("""{"error":"${e.message}"}""", Response.Status.INTERNAL_ERROR)
            }
        }

        private fun handleScreenshot(): Response {
            // Use AccessibilityService screenshot (Android 14+)
            val accService = CuaAccessibilityService.instance
            if (accService != null) {
                accService.takeScreenshot()
                Thread.sleep(1000)
                val bytes = lastScreenshotBytes
                if (bytes != null) {
                    return newFixedLengthResponse(Response.Status.OK, "image/png", bytes)
                }
            }
            return jsonResponse("""{"error":"screenshot failed"}""", Response.Status.INTERNAL_ERROR)
        }

        private fun handleTap(session: IHTTPSession): Response {
            val params = parseBodyAndParams(session)
            val x = params["x"]?.toFloatOrNull() ?: return jsonResponse("""{"error":"missing x"}""", Response.Status.BAD_REQUEST)
            val y = params["y"]?.toFloatOrNull() ?: return jsonResponse("""{"error":"missing y"}""", Response.Status.BAD_REQUEST)

            CuaAccessibilityService.instance?.performTap(x, y)
            return jsonResponse("""{"status":"ok","x":$x,"y":$y}""")
        }

        private fun handleSwipe(session: IHTTPSession): Response {
            val params = parseBodyAndParams(session)
            val x1 = params["x1"]?.toFloatOrNull() ?: return jsonResponse("""{"error":"missing x1"}""")
            val y1 = params["y1"]?.toFloatOrNull() ?: return jsonResponse("""{"error":"missing y1"}""")
            val x2 = params["x2"]?.toFloatOrNull() ?: return jsonResponse("""{"error":"missing x2"}""")
            val y2 = params["y2"]?.toFloatOrNull() ?: return jsonResponse("""{"error":"missing y2"}""")

            CuaAccessibilityService.instance?.performSwipe(x1, y1, x2, y2)
            return jsonResponse("""{"status":"ok"}""")
        }

        private fun handleText(session: IHTTPSession): Response {
            val params = parseBodyAndParams(session)
            val text = params["text"] ?: return jsonResponse("""{"error":"missing text"}""")
            CuaAccessibilityService.instance?.performText(text)
            return jsonResponse("""{"status":"ok"}""")
        }

        private fun handleKey(session: IHTTPSession): Response {
            val params = parseBodyAndParams(session)
            val key = params["key"] ?: return jsonResponse("""{"error":"missing key"}""")

            val actionId = when (key.uppercase()) {
                "BACK" -> android.accessibilityservice.GestureDescription.GESTURE_GLOBAL_ACTION_BACK
                "HOME" -> android.accessibilityservice.GestureDescription.GESTURE_GLOBAL_ACTION_HOME
                "RECENTS" -> android.accessibilityservice.GestureDescription.GESTURE_GLOBAL_ACTION_RECENTS
                "NOTIFICATIONS" -> android.accessibilityservice.GestureDescription.GESTURE_GLOBAL_ACTION_NOTIFICATIONS
                else -> return jsonResponse("""{"error":"unknown key: $key"}""")
            }

            CuaAccessibilityService.instance?.performGlobalAction(actionId)
            return jsonResponse("""{"status":"ok","key":"$key"}""")
        }

        private fun handleUiDump(): Response {
            val accService = CuaAccessibilityService.instance
            if (accService == null) {
                return jsonResponse("""{"error":"accessibility service not connected"}""")
            }
            val uiXml = accService.getUiHierarchy()
            return newFixedLengthResponse(Response.Status.OK, "application/xml", uiXml ?: "<empty/>")
        }

        private fun parseBodyAndParams(session: IHTTPSession): Map<String, String> {
            val params = mutableMapOf<String, String>()
            // Parse query params
            session.parms?.let { params.putAll(it) }
            // Parse body as JSON
            try {
                val body = ByteArrayOutputStream()
                session.inputStream?.copyTo(body)
                val bodyStr = String(body.toByteArray())
                if (bodyStr.isNotBlank()) {
                    val json = com.google.gson.JsonParser.parseString(bodyStr).asJsonObject
                    json.entrySet().forEach { (key, value) ->
                        if (!params.containsKey(key)) {
                            params[key] = value.asString
                        }
                    }
                }
            } catch (_: Exception) {}
            return params
        }

        private fun jsonResponse(json: String, status: Response.Status = Response.Status.OK): Response {
            return newFixedLengthResponse(status, "application/json", json)
        }

        private fun getDeviceInfo(): String {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            wm.defaultDisplay.getMetrics(metrics)
            return """
            {
                "device":"${Build.MODEL}",
                "manufacturer":"${Build.MANUFACTURER}",
                "sdk":${Build.VERSION.SDK_INT},
                "release":"${Build.VERSION.RELEASE}",
                "width":${metrics.widthPixels},
                "height":${metrics.heightPixels},
                "density":${metrics.densityDpi}
            }
            """.trimIndent()
        }
    }
}
