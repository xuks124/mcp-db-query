package com.hermes.cua;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {

    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        Button btnAccess = findViewById(R.id.btnAccessibility);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnScreenshot = findViewById(R.id.btnScreenshot);

        updateStatus();

        // Request Shizuku permission
        Shizuku.addRequestPermissionResultListener(new Shizuku.OnRequestPermissionResultListener() {
            @Override
            public void onRequestPermissionResult(int requestCode, int grantResult) {
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MainActivity.this, "✅ Shizuku已授权", Toast.LENGTH_SHORT).show();
                    CuaService.hasShizuku = true;
                }
            }
        });
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "⚠ Shizuku未运行，截图需授权", Toast.LENGTH_LONG).show();
        } else if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(0);
        } else {
            CuaService.hasShizuku = true;
        }

        btnAccess.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(MainActivity.this, "找到 Hermes CUA 并开启", Toast.LENGTH_LONG).show();
        });

        btnStart.setOnClickListener(v -> {
            if (CuaService.isRunning) {
                Toast.makeText(MainActivity.this, "服务已在运行中", Toast.LENGTH_SHORT).show();
                updateStatus();
                return;
            }
            Intent svc = new Intent(MainActivity.this, CuaService.class);
            startForegroundService(svc);
            Toast.makeText(MainActivity.this, "Hermes CUA 已启动", Toast.LENGTH_SHORT).show();
            updateStatus();
        });

        btnScreenshot.setOnClickListener(v -> {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            if (mpm == null) {
                Toast.makeText(this, "设备不支持 MediaProjection", Toast.LENGTH_LONG).show();
                return;
            }
            Intent intent = mpm.createScreenCaptureIntent();
            startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // Store the result for CuaService to pick up
                CuaService.pendingResultCode = resultCode;
                CuaService.pendingData = data;
                // If service is already running, try to setup projection now
                if (CuaService.isRunning) {
                    Intent svc = new Intent(MainActivity.this, CuaService.class);
                    startForegroundService(svc);
                }
                Toast.makeText(this, "✅ 截图权限已获取！", Toast.LENGTH_SHORT).show();
                updateStatus();
            } else {
                Toast.makeText(this, "❌ 用户拒绝了截图权限", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        StringBuilder sb = new StringBuilder();
        if (CuaService.isRunning) {
            sb.append("✅ 服务运行中\n端口: 8640\n");
            if (CuaService.pendingResultCode != -1 && CuaService.pendingData != null) {
                sb.append("✅ 截图权限已授予\n");
            } else {
                sb.append("⚠️ 截图权限未授予\n");
            }
            sb.append("Hermes 可以连接了");
        } else {
            sb.append("⏳ 服务未启动\n请先启用无障碍服务，再启动服务\n然后授予截图权限");
        }
        statusText.setText(sb.toString());
    }
}
// Build trigger: MediaProjection fix 1778996086
