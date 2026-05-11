package com.hermes.cua;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Path;
import android.hardware.HardwareBuffer;
import android.os.Bundle;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CuaAccessibilityService extends AccessibilityService {

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        CuaService.setAccService(this);
        android.util.Log.i("CuaAcc", "Service connected OK");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    public void tap(int x, int y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            android.util.Log.e("CuaAcc", "tap failed", e);
        }
    }

    public void swipe(int x1, int y1, int x2, int y2, int durMs) {
        try {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durMs));
            dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            android.util.Log.e("CuaAcc", "swipe failed", e);
        }
    }

    public String dumpUi() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "<empty/>";
            String result = dumpNodeSimple(root);
            root.recycle();
            return result;
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private String dumpNodeSimple(AccessibilityNodeInfo node) {
        // Minimal XML dump without deep recursion overhead
        StringBuilder sb = new StringBuilder();
        dumpNodeXml(node, sb, 0, 50); // max depth 50
        return sb.toString();
    }

    private void dumpNodeXml(AccessibilityNodeInfo node, StringBuilder sb, int depth, int maxDepth) {
        if (node == null || depth >= maxDepth) return;
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append("<node");
        appendAttr(sb, "class", node.getClassName());
        appendAttr(sb, "text", node.getText());
        appendAttr(sb, "desc", node.getContentDescription());
        appendAttr(sb, "id", node.getViewIdResourceName());
        android.graphics.Rect bounds = new android.graphics.Rect();
        node.getBoundsInScreen(bounds);
        sb.append(" bounds=\"[").append(bounds.left).append(",").append(bounds.top)
          .append("][").append(bounds.right).append(",").append(bounds.bottom).append("]\"");
        sb.append(" clickable=\"").append(node.isClickable()).append("\"");
        
        int childCount = node.getChildCount();
        if (childCount == 0) {
            sb.append("/>\n");
        } else {
            sb.append(">\n");
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                dumpNodeXml(child, sb, depth + 1, maxDepth);
                if (child != null) child.recycle();
            }
            for (int i = 0; i < depth; i++) sb.append("  ");
            sb.append("</node>\n");
        }
    }

    private void appendAttr(StringBuilder sb, String name, CharSequence value) {
        if (value != null && value.length() > 0) {
            String s = value.toString().replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("\n", "\\n");
            sb.append(" ").append(name).append("=\"").append(s).append("\"");
        }
    }

    public void goBack() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public void goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public void inputText(String text) {
        try {
            AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    root.recycle();
                }
            }
            if (focused != null) {
                if (focused.isEditable()) {
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
                }
                focused.recycle();
            }
        } catch (Exception e) {
            android.util.Log.e("CuaAcc", "inputText failed", e);
        }
    }

    public byte[] captureScreen() {
        if (Build.VERSION.SDK_INT < 34) return null;
        try {
            final Bitmap[] result = new Bitmap[1];
            final CountDownLatch latch = new CountDownLatch(1);
            takeScreenshot(
                1, // TAKE_SCREENSHOT_FULL_SCREEN (@hide, value=1)
                Executors.newSingleThreadExecutor(),
                new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult sr) {
                        try {
                            HardwareBuffer hb = sr.getHardwareBuffer();
                            if (hb != null) {
                                Bitmap bmp = Bitmap.wrapHardwareBuffer(hb,
                                    ColorSpace.get(ColorSpace.Named.SRGB));
                                if (bmp != null) {
                                    result[0] = bmp.copy(bmp.getConfig(), bmp.isMutable());
                                    bmp.recycle();
                                }
                                hb.close();
                            }
                        } catch (Exception e) {
                            android.util.Log.e("CuaAcc", "captureScreen fail", e);
                        }
                        latch.countDown();
                    }
                    @Override
                    public void onFailure(int errorCode) {
                        android.util.Log.e("CuaAcc", "captureScreen error " + errorCode);
                        latch.countDown();
                    }
                }
            );
            latch.await(5, TimeUnit.SECONDS);
            if (result[0] != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                result[0].compress(Bitmap.CompressFormat.PNG, 90, baos);
                result[0].recycle();
                return baos.toByteArray();
            }
        } catch (Exception e) {
            android.util.Log.e("CuaAcc", "captureScreen exception", e);
        }
        return null;
    }

    public boolean pasteClipboard() {
        try {
            AccessibilityNodeInfo focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                    root.recycle();
                }
            }
            if (focused != null) {
                boolean ok = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                focused.recycle();
                return ok;
            }
        } catch (Exception e) {
            android.util.Log.e("CuaAcc", "paste failed", e);
        }
        return false;
    }
}
