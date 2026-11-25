package com.couchcrypt.android;

import android.os.Bundle;
import android.os.Build;
import android.view.WindowManager;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.couchcrypt.CryptLobby;
import com.couchcrypt.GameConfig;

public final class AndroidLauncher extends AndroidApplication {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useAccelerometer = false; config.useCompass = false; config.useGyroscope = false;
        config.disableAudio = false; config.numSamples = 0;
        config.r = 8; config.g = 8; config.b = 8; config.a = 0; config.depth = 0; config.stencil = 0;
        String host = getIntent().getStringExtra("joinHost");
        int port = getIntent().getIntExtra("joinPort", GameConfig.DEFAULT_PORT);
        // The emulator's own 10.0.2.x address is unreachable from phones. Keep
        // the development launcher's LAN override across ordinary icon launches
        // and process recreation. Physical TVs continue discovering their LAN IP.
        if ("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE)) {
            var saved = getSharedPreferences("emulator_join", MODE_PRIVATE);
            if (getIntent().hasExtra("joinHost")) {
                var edit = saved.edit();
                if (host == null || host.trim().isEmpty()) edit.clear();
                else edit.putString("host", host).putInt("port", port);
                edit.apply();
            } else {
                host = saved.getString("host", null);
                port = getIntent().getIntExtra("joinPort", saved.getInt("port", GameConfig.DEFAULT_PORT));
            }
        }
        boolean profile = getIntent().getBooleanExtra("profile", false);
        initialize(new CryptLobby(new GameConfig(host, port, profile)), config);
    }
}
