package com.hermes.cua;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class CuaAccessibilityService extends AccessibilityService {

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        CuaService.setAccService(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    public void tap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        dispatchGesture(builder.build(), null, null);
    }

    public void swipe(int x1, int y1, int x2, int y2, int durMs) {
        Path path = new Path();
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durMs));
        dispatchGesture(builder.build(), null, null);
    }

    public String dumpUi() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return "<empty/>";
        StringBuilder sb = new StringBuilder();
        dumpNode(root, sb, 0);
        root.recycle();
        return sb.toString();
    }

    private void dumpNode(AccessibilityNodeInfo node, StringBuilder sb, int depth) {
        if (node == null) return;
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append("<node");
        if (node.getClassName() != null) {
            sb.append(" class=\"").append(escape(node.getClassName().toString())).append("\"");
        }
        if (node.getText() != null) {
            sb.append(" text=\"").append(escape(node.getText().toString())).append("\"");
        }
        if (node.getContentDescription() != null) {
            sb.append(" desc=\"").append(escape(node.getContentDescription().toString())).append("\"");
        }
        if (node.getViewIdResourceName() != null) {
            sb.append(" id=\"").append(escape(node.getViewIdResourceName())).append("\"");
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        sb.append(" bounds=\"[").append(bounds.left).append(",").append(bounds.top)
          .append("][").append(bounds.right).append(",").append(bounds.bottom).append("]\"");
        sb.append(" clickable=\"").append(node.isClickable()).append("\"");
        sb.append(">\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            dumpNode(node.getChild(i), sb, depth + 1);
        }
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
