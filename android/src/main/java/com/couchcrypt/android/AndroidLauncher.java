package com.couchcrypt.android;

import android.os.Bundle;
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
        config.disableAudio = true; config.numSamples = 0;
        config.r = 8; config.g = 8; config.b = 8; config.a = 0; config.depth = 0; config.stencil = 0;
        String host = getIntent().getStringExtra("joinHost");
        int port = getIntent().getIntExtra("joinPort", GameConfig.DEFAULT_PORT);
        boolean profile = getIntent().getBooleanExtra("profile", false);
        initialize(new CryptLobby(new GameConfig(host, port, profile)), config);
    }
}
