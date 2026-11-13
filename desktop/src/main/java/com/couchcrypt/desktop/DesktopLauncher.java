package com.couchcrypt.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.couchcrypt.CryptLobby;
import com.couchcrypt.GameConfig;

public final class DesktopLauncher {
    public static void main(String[] args) {
        String host = null; int port = GameConfig.DEFAULT_PORT; boolean profile = false;
        for (String arg : args) {
            if (arg.startsWith("--host=")) host = arg.substring(7);
            else if (arg.startsWith("--port=")) port = Integer.parseInt(arg.substring(7));
            else if (arg.equals("--profile")) profile = true;
            else throw new IllegalArgumentException("Unknown argument: " + arg);
        }
        Lwjgl3ApplicationConfiguration window = new Lwjgl3ApplicationConfiguration();
        window.setTitle("Couch Crypt"); window.setWindowedMode(1280, 720);
        window.setWindowSizeLimits(960, 540, -1, -1);
        window.useVsync(true); window.setForegroundFPS(60); window.setIdleFPS(20);
        window.setBackBufferConfig(8, 8, 8, 0, 0, 0, 0);
        window.disableAudio(false);
        new Lwjgl3Application(new CryptLobby(new GameConfig(host, port, profile)), window);
    }
}
