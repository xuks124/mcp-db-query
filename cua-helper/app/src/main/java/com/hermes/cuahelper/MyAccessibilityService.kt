package com.hermes.cuahelper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    companion object {
        var instance: MyAccessibilityService? = null
            private set

        fun getRootNode(): AccessibilityNodeInfo? {
            return try {
                instance?.rootInActiveWindow
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for now
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
