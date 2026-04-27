package com.hermes.cua;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;

public class MainActivity extends Activity {

    static final int REQUEST_MEDIA_PROJECTION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if already running
        if (CuaService.isRunning) {
            Toast.makeText(this, "Hermes CUA 已在运行", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Request MediaProjection for screenshots
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                // Store in static fields so CuaService can pick them up
                CuaService.pendingResultCode = resultCode;
                CuaService.pendingData = data;

                Intent svc = new Intent(this, CuaService.class);
                startForegroundService(svc);

                Toast.makeText(this, "Hermes CUA 已启动", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要屏幕捕获权限才能使用", Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
}
