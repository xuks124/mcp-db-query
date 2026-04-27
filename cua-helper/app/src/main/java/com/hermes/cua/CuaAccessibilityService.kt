package com.hermes.cua

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class CuaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: CuaAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events, just provide services
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    /**
     * Take a screenshot using the AccessibilityService API (Android 14+).
     */
    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14
            takeScreenshot(
                SCREENSHOT_CHROME_HIDE_SCREENSHOT,
                object : TakeScreenshotCallback {
                    override fun onScreenshotTaken(screenshot: ScreenshotResult) {
                        val hardwareBuffer = screenshot.hardwareBuffer
                        if (hardwareBuffer != null) {
                            val bitmap = android.graphics.Bitmap.wrapHardwareBuffer(
                                hardwareBuffer, null
                            )
                            val stream = java.io.ByteArrayOutputStream()
                            bitmap?.compress(
                                android.graphics.Bitmap.CompressFormat.PNG, 90, stream
                            )
                            CuaService.lastScreenshotBytes = stream.toByteArray()
                            hardwareBuffer.close()
                        }
                    }

                    override fun onScreenshotFailed() {
                        // Failed to take screenshot
                    }
                },
                handler
            )
        }
    }

    /**
     * Perform a tap gesture at (x, y).
     */
    fun performTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Perform a swipe gesture.
     */
    fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * Type text using AccessibilityService's ability to set text.
     * This works when a text field is focused.
     */
    fun performText(text: String) {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null) {
            val args = android.os.Bundle()
            // Use ACTION_SET_TEXT for full replace
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
            focused.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                args
            )
        }
        root.recycle()
    }

    /**
     * Perform a global action (back, home, recents).
     */
    fun performGlobalAction(action: Int) {
        performGlobalAction(
            when (action) {
                android.accessibilityservice.GestureDescription.GESTURE_GLOBAL_ACTION_BACK
                    -> GLOBAL_ACTION_BACK
                android.accessibilityservice.GestureDescription.GESTURE_GLOBAL_ACTION_HOME
                    -> GLOBAL_ACTION_HOME
                android.accessibilityservice.GestureDescription.GESTURE_GLOBAL_ACTION_RECENTS
                    -> GLOBAL_ACTION_RECENTS
                android.accessibilityservice.GestureDescription.GESTURE_GLOBAL_ACTION_NOTIFICATIONS
                    -> GLOBAL_ACTION_NOTIFICATIONS
                else -> GLOBAL_ACTION_BACK
            }
        )
    }

    /**
     * Get the current UI hierarchy as XML.
     */
    fun getUiHierarchy(): String? {
        val root = rootInActiveWindow ?: return null
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
            val rootElement = doc.createElement("hierarchy")
            doc.appendChild(rootElement)
            serializeNode(root, rootElement, doc)
            root.recycle()

            val transformer = TransformerFactory.newInstance().newTransformer()
            val result = StreamResult(StringWriter())
            transformer.transform(DOMSource(doc), result)
            result.writer.toString()
        } catch (e: Exception) {
            root.recycle()
            "<error>${e.message}</error>"
        }
    }

    private fun serializeNode(node: AccessibilityNodeInfo, parent: org.w3c.dom.Element, doc: org.w3c.dom.Document) {
        val element = doc.createElement("node")
        element.setAttribute("class", node.className?.toString() ?: "")
        element.setAttribute("text", node.text?.toString() ?: "")
        element.setAttribute("content-desc", node.contentDescription?.toString() ?: "")
        element.setAttribute("resource-id", node.viewIdResourceName ?: "")
        element.setAttribute("clickable", node.isClickable.toString())
        element.setAttribute("focusable", node.isFocusable.toString())
        element.setAttribute("scrollable", node.isScrollable.toString())
        element.setAttribute("checked", node.isChecked.toString())
        element.setAttribute("enabled", node.isEnabled.toString())
        element.setAttribute("password", node.isPassword.toString())
        element.setAttribute("selected", node.isSelected.toString())

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        element.setAttribute("bounds", "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")

        parent.appendChild(element)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            serializeNode(child, element, doc)
            child.recycle()
        }
    }
}
