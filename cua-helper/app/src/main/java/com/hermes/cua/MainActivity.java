package com.hermes.cua;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {

    static final int REQUEST_MEDIA_PROJECTION = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check if Accessibility Service is enabled
        String service = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (service == null || !service.contains(getPackageName())) {
            Toast.makeText(this, "请在无障碍设置中启用 Hermes CUA", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
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
                Intent svc = new Intent(this, CuaService.class);
                svc.putExtra("resultCode", resultCode);
                svc.putExtra("data", data);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc);
                } else {
                    startService(svc);
                }
                Toast.makeText(this, "Hermes CUA 已启动", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要屏幕捕获权限", Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }
}
