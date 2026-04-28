package com.hermes.cua;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        Button btnAccess = findViewById(R.id.btnAccessibility);
        Button btnStart = findViewById(R.id.btnStart);

        // Check current status
        updateStatus();

        btnAccess.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
                Toast.makeText(MainActivity.this, "找到 Hermes CUA 并开启", Toast.LENGTH_LONG).show();
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (CuaService.isRunning) {
                    Toast.makeText(MainActivity.this, "服务已在运行中", Toast.LENGTH_SHORT).show();
                    updateStatus();
                    return;
                }
                Intent svc = new Intent(MainActivity.this, CuaService.class);
                startForegroundService(svc);
                Toast.makeText(MainActivity.this, "Hermes CUA 已启动", Toast.LENGTH_SHORT).show();
                updateStatus();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        if (CuaService.isRunning) {
            statusText.setText("✅ 服务运行中\n端口: 8640\nHermes 可以连接了");
        } else {
            statusText.setText("⏳ 服务未启动\n请先启用无障碍服务，再启动服务");
        }
    }
}
