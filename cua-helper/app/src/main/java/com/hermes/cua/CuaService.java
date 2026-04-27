package com.hermes.cua;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class CuaService extends Service {

    // Static fields for passing MediaProjection data from activity
    public static int pendingResultCode = -1;
    public static Intent pendingData = null;
    public static boolean isRunning = false;

    private static CuaAccessibilityService accService;

    public static void setAccService(CuaAccessibilityService s) {
        accService = s;
    }

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int width, height, densityDpi;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    "cua", "CUA", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            startForeground(1, new Notification.Builder(this, "cua")
                    .setContentTitle("Hermes CUA")
                    .setContentText("运行中 · :8640")
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .build());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start HTTP server FIRST, so we can always connect
        if (!running) {
            startHttp();
            isRunning = true;
        }

        // Then try to set up screen capture (may fail on some devices)
        if (pendingResultCode != -1 && pendingData != null) {
            try {
                setupProjection(pendingResultCode, pendingData);
            } catch (Exception e) {
                android.util.Log.e("CuaService", "setupProjection failed", e);
            }
            pendingResultCode = -1;
            pendingData = null;
        }
        return START_STICKY;
    }

    private void setupProjection(int resultCode, Intent data) {
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (mpm == null) throw new RuntimeException("MediaProjectionManager not available");
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) throw new RuntimeException("getMediaProjection returned null");

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        width = dm.widthPixels;
        height = dm.heightPixels;
        densityDpi = dm.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "cua", width, height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    private void startHttp() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(8640);
                while (running) {
                    try {
                        Socket s = serverSocket.accept();
                        new Thread(() -> handle(s)).start();
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handle(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            byte[] buf = new byte[4096];
            int n = in.read(buf);
            if (n <= 0) { socket.close(); return; }
            String req = new String(buf, 0, n);
            String[] lines = req.split("\r\n");
            if (lines.length == 0) { socket.close(); return; }

            String[] parts = lines[0].split(" ");
            if (parts.length < 2) { socket.close(); return; }

            String method = parts[0];
            String path = parts[1];
            String query = "";
            if (path.contains("?")) {
                String[] p = path.split("\\?", 2);
                path = p[0];
                query = p[1];
            }

            try {
                if (path.equals("/screenshot")) {
                    serveScreenshot(out);
                } else if (path.equals("/tap")) {
                    int x = getIntParam(query, "x", 0);
                    int y = getIntParam(query, "y", 0);
                    if (accService != null) { accService.tap(x, y); textResponse(out, "OK");
                    } else textResponse(out, "503: acc service not ready", 503);
                } else if (path.equals("/swipe")) {
                    int x1 = getIntParam(query, "x1", 0);
                    int y1 = getIntParam(query, "y1", 0);
                    int x2 = getIntParam(query, "x2", 0);
                    int y2 = getIntParam(query, "y2", 0);
                    int dur = getIntParam(query, "dur", 300);
                    if (accService != null) { accService.swipe(x1, y1, x2, y2, dur); textResponse(out, "OK");
                    } else textResponse(out, "503: acc service not ready", 503);
                } else if (path.equals("/key")) {
                    String code = getParam(query, "code", "");
                    if (accService != null) {
                        int action = -1;
                        if (code.equalsIgnoreCase("BACK")) action = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;
                        else if (code.equalsIgnoreCase("HOME")) action = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME;
                        else if (code.equalsIgnoreCase("RECENTS")) action = android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS;
                        if (action >= 0) accService.performGlobalAction(action);
                        textResponse(out, "OK");
                    } else textResponse(out, "503: acc service not ready", 503);
                } else if (path.equals("/uidump")) {
                    if (accService != null) textResponse(out, accService.dumpUi());
                    else textResponse(out, "503: acc service not ready", 503);
                } else if (path.equals("/info")) {
                    textResponse(out, "width=" + width + "&height=" + height + "&density=" + densityDpi);
                } else if (path.equals("/clipboard")) {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    String text = getParam(query, "value", "");
                    if (text.isEmpty()) {
                        ClipData cd = cm.getPrimaryClip();
                        String clip = (cd != null && cd.getItemCount() > 0)
                                ? cd.getItemAt(0).getText().toString() : "";
                        textResponse(out, clip);
                    } else {
                        cm.setPrimaryClip(ClipData.newPlainText("cua", text));
                        textResponse(out, "OK");
                    }
                } else {
                    textResponse(out, "404", 404);
                }
            } catch (Exception e) {
                textResponse(out, "500: " + e.getMessage(), 500);
            }

            socket.close();
        } catch (Exception e) {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private void serveScreenshot(OutputStream out) throws IOException {
        if (imageReader == null) {
            textResponse(out, "503: MediaProjection not ready", 503);
            return;
        }
        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            textResponse(out, "500: no image", 500);
            return;
        }
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int ps = planes[0].getPixelStride();
        int rs = planes[0].getRowStride();
        int padding = rs - ps * width;
        Bitmap bitmap = Bitmap.createBitmap(width + padding / ps, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        if (padding > 0) bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        image.close();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos);
        byte[] bytes = baos.toByteArray();

        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 200 OK\r\n");
        headers.append("Content-Type: image/png\r\n");
        headers.append("Content-Length: ").append(bytes.length).append("\r\n");
        headers.append("Connection: close\r\n\r\n");
        out.write(headers.toString().getBytes());
        out.write(bytes);
        out.flush();
    }

    private void textResponse(OutputStream out, String text) throws IOException {
        textResponse(out, text, 200);
    }

    private void textResponse(OutputStream out, String text, int code) throws IOException {
        byte[] bytes = text.getBytes("UTF-8");
        StringBuilder headers = new StringBuilder();
        headers.append("HTTP/1.1 ").append(code).append(" ");
        if (code == 200) headers.append("OK");
        else if (code == 404) headers.append("Not Found");
        else if (code == 500) headers.append("Internal Error");
        else if (code == 503) headers.append("Service Unavailable");
        headers.append("\r\n");
        headers.append("Content-Type: text/plain; charset=UTF-8\r\n");
        headers.append("Content-Length: ").append(bytes.length).append("\r\n");
        headers.append("Connection: close\r\n\r\n");
        out.write(headers.toString().getBytes());
        out.write(bytes);
        out.flush();
    }

    private int getIntParam(String query, String key, int def) {
        try { return Integer.parseInt(getParam(query, key, String.valueOf(def))); }
        catch (Exception e) { return def; }
    }

    private String getParam(String query, String key, String def) {
        if (query == null) return def;
        for (String p : query.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key))
                return URLDecoder.decode(kv[1]);
        }
        return def;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        try { if (imageReader != null) imageReader.close(); } catch (Exception ignored) {}
        try { if (mediaProjection != null) mediaProjection.stop(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
