package com.couchcrypt;

public record GameConfig(String advertisedHost, int port, boolean profile) {
    public static final int DEFAULT_PORT = 8787;
}
