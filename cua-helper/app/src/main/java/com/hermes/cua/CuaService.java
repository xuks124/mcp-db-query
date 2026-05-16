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
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final AtomicBoolean running = new AtomicBoolean(false);
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
        if (!running.get()) {
            startHttp();
            isRunning = true;
        }

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
        if (mpm == null) { android.util.Log.e("CuaService", "mpm null"); return; }
        mediaProjection = mpm.getMediaProjection(resultCode, data);
        if (mediaProjection == null) { android.util.Log.e("CuaService", "mediaProjection null"); return; }

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        try {
            wm.getDefaultDisplay().getRealMetrics(dm);
        } catch (Exception e) {
            wm.getDefaultDisplay().getMetrics(dm);
            android.util.Log.e("CuaService", "getRealMetrics failed", e);
        }
        width = dm.widthPixels;
        height = dm.heightPixels;
        densityDpi = dm.densityDpi;

        try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        } catch (Exception e) {
            android.util.Log.e("CuaService", "ImageReader failed", e);
            return;
        }
        if (imageReader == null) {
            android.util.Log.e("CuaService", "imageReader null");
            return;
        }
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "cua", width, height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    private void startHttp() {
        running.set(true);
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(8640);
                serverSocket.setReuseAddress(true);
                serverSocket.setSoTimeout(2000);
                android.util.Log.i("CuaService", "HTTP server started on :8640");
                while (running.get()) {
                    Socket clientSocket = null;
                    try {
                        clientSocket = serverSocket.accept();
                        final Socket s = clientSocket;
                        // Handle each request in its own thread - prevents single stuck request blocking all others
                        new Thread(() -> handleRequest(s)).start();
                    } catch (SocketTimeoutException ste) {
                        if (clientSocket != null) try { clientSocket.close(); } catch (Exception ignored) {}
                    } catch (Exception e) {
                        android.util.Log.e("CuaService", "accept error: " + e.getMessage());
                        if (clientSocket != null) try { clientSocket.close(); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("CuaService", "server died: " + e.getMessage());
            }
        }).start();
    }

    private void handleRequest(Socket s) {
        try {
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, "UTF-8"));
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                s.close();
                return;
            }

            // Consume headers
            String header;
            while ((header = reader.readLine()) != null && !header.isEmpty()) {
                // consume
            }

            String[] parts = requestLine.split(" ", 3);
            String path = parts.length > 1 ? parts[1] : "/";
            String query = "";
            int qIdx = path.indexOf('?');
            if (qIdx >= 0) {
                query = path.substring(qIdx + 1);
                path = path.substring(0, qIdx);
            }

            OutputStream out = s.getOutputStream();
            boolean alreadyClosed = false;

            if (path.equals("/screenshot") || path.equals("/screen")) {
                serveScreenshot(out);
            } else if (path.equals("/ss2")) {
                // Debug: accessibility screenshot only - returns JSON
                if (accService == null) {
                    textResponse(out, "{\"error\":\"no accService\"}");
                } else {
                    byte[] png = accService.captureScreen();
                    textResponse(out, "{\"png\":" + (png == null ? "null" : ("" + png.length)) + ",\"sdk\":" + Build.VERSION.SDK_INT + ",\"status\":\"" + CuaAccessibilityService.lastCaptureError + "\"}");
                }
            } else if (path.equals("/dump")) {
                if (accService != null) {
                    final String[] dumpResult = new String[1];
                    final CountDownLatch dumpLatch = new CountDownLatch(1);
                    handler.post(() -> {
                        try { dumpResult[0] = accService.dumpUi(); }
                        catch (Exception e) { dumpResult[0] = "{\"error\":\"" + e.getMessage() + "\"}"; }
                        dumpLatch.countDown();
                    });
                    if (dumpLatch.await(2, TimeUnit.SECONDS)) {
                        textResponse(out, dumpResult[0]);
                    } else {
                        textResponse(out, "{\"error\":\"dump timeout\"}");
                    }
                } else textResponse(out, "{\"error\":\"no accessibility service\"}");
            } else if (path.equals("/status") || path.equals("/") || path.equals("/ping")) {
                textResponse(out, "OK");
            } else if (path.equals("/click")) {
                int x = getIntParam(query, "x", 0);
                int y = getIntParam(query, "y", 0);
                textResponse(out, "{\"success\":true}");
                out.flush();
                s.shutdownOutput();
                s.close();
                alreadyClosed = true;
                if (accService != null) {
                    final int fx = x, fy = y;
                    final CuaAccessibilityService as = accService;
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> as.tap(fx, fy), 50);
                }
            } else if (path.equals("/swipe")) {
                int x1 = getIntParam(query, "x1", 0);
                int y1 = getIntParam(query, "y1", 0);
                int x2 = getIntParam(query, "x2", 0);
                int y2 = getIntParam(query, "y2", 0);
                int dur = getIntParam(query, "duration", 300);
                textResponse(out, "{\"success\":true}");
                out.flush();
                s.shutdownOutput();
                s.close();
                alreadyClosed = true;
                if (accService != null) {
                    final int fx1 = x1, fy1 = y1, fx2 = x2, fy2 = y2, fdur = dur;
                    final CuaAccessibilityService as = accService;
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> as.swipe(fx1, fy1, fx2, fy2, fdur), 50);
                }
            } else if (path.equals("/back")) {
                textResponse(out, "{\"success\":true}");
                out.flush();
                s.shutdownOutput();
                s.close();
                alreadyClosed = true;
                if (accService != null) {
                    final CuaAccessibilityService as = accService;
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> as.goBack(), 50);
                }
            } else if (path.equals("/home")) {
                textResponse(out, "{\"success\":true}");
                out.flush();
                s.shutdownOutput();
                s.close();
                alreadyClosed = true;
                if (accService != null) {
                    final CuaAccessibilityService as = accService;
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> as.goHome(), 50);
                }
            } else if (path.equals("/input")) {
                String text = getParam(query, "text", "");
                if (accService != null && !text.isEmpty()) {
                    final String ft = text;
                    final CuaAccessibilityService as = accService;
                    textResponse(out, "{\"success\":true}");
                    out.flush();
                    s.shutdownOutput();
                    s.close();
                    alreadyClosed = true;
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> as.inputText(ft), 50);
                } else {
                    textResponse(out, "{\"success\":false,\"error\":\"no text or service\"}");
                }
            } else if (path.equals("/openapp")) {
                String pkg = getParam(query, "pkg", "");
                if (pkg.isEmpty()) {
                    textResponse(out, "{\"success\":false,\"error\":\"missing pkg\"}");
                } else {
                    try {
                        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                        if (i != null) {
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK 
                                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(i);
                            textResponse(out, "{\"success\":true}");
                        } else {
                            textResponse(out, "{\"success\":false,\"error\":\"app not found\"}");
                        }
                    } catch (Exception e) {
                        textResponse(out, "{\"success\":false,\"error\":\"" + e.getMessage() + "\"}");
                    }
                }
            } else if (path.equals("/copy")) {
                String text = getParam(query, "text", "");
                if (text.isEmpty()) {
                    textResponse(out, "{\"success\":false,\"error\":\"missing text\"}");
                } else {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("cua", text));
                    textResponse(out, "{\"success\":true}");
                }
            } else if (path.equals("/paste")) {
                if (accService != null) {
                    boolean ok = accService.pasteClipboard();
                    textResponse(out, "{\"success\":" + ok + "}");
                } else {
                    textResponse(out, "{\"success\":false,\"error\":\"no acc service\"}");
                }
            } else if (path.equals("/recents")) {
                if (accService != null) {
                    final CuaAccessibilityService as = accService;
                    textResponse(out, "{\"success\":true}");
                    out.flush();
                    s.shutdownOutput();
                    s.close();
                    alreadyClosed = true;
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> as.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS), 100);
                } else {
                    textResponse(out, "{\"success\":false,\"error\":\"no acc service\"}");
                }
            } else {
                textResponse(out, "404", 404);
            }
            if (!alreadyClosed) {
                out.flush();
                s.shutdownOutput();
                s.close();
            }
        } catch (SocketTimeoutException ste) {
            android.util.Log.w("CuaService", "request timeout");
            try { s.close(); } catch (Exception ignored) {}
        } catch (Exception e) {
            android.util.Log.e("CuaService", "handler: " + e.getMessage());
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private void serveScreenshot(OutputStream out) throws IOException {
        // Try MediaProjection first
        if (imageReader != null) {
            Image image = imageReader.acquireLatestImage();
            if (image != null) {
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
                sendPng(out, baos.toByteArray());
                return;
            }
        }
        // Fallback: AccessibilityService screenshot (API 34+, works on HyperOS)
        if (accService != null && Build.VERSION.SDK_INT >= 34) {
            try {
                byte[] png = accService.captureScreen();
                if (png != null && png.length > 100) {
                    sendPng(out, png);
                    return;
                }
            } catch (Exception e) {
                android.util.Log.e("CuaService", "acc screenshot failed", e);
            }
        }
        textResponse(out, "503", 503);
    }

    private void sendPng(OutputStream out, byte[] bytes) throws IOException {
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
        running.set(false);
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch (Exception ignored) {}
        try { if (imageReader != null) imageReader.close(); } catch (Exception ignored) {}
        try { if (mediaProjection != null) mediaProjection.stop(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
