package com.couchcrypt.game;

/** Stable colour and stat roles that reuse the existing painted enemy frames. */
public enum EnemyVariant {
    ORIGINAL(1f, 1f, 1f, 1f, 1f, 1f),
    MOSS(.67f, 1f, .74f, 1.40f, .85f, 1f),
    FROST(.22f, .80f, 1f, .80f, 1.25f, 1f),
    EMBER(1f, .70f, .56f, .85f, 1f, 1.25f),
    DUSK(.18f, 1f, 1f, 1.15f, 1.10f, .85f);

    public final float red, green, blue;
    public final float healthMultiplier, speedMultiplier, damageMultiplier;

    EnemyVariant(float red, float green, float blue, float healthMultiplier, float speedMultiplier, float damageMultiplier) {
        this.red = red; this.green = green; this.blue = blue;
        this.healthMultiplier = healthMultiplier; this.speedMultiplier = speedMultiplier; this.damageMultiplier = damageMultiplier;
    }

    /** A separate spawn hash avoids extra combat random draws or tying variants to reusable pool slots. */
    static EnemyVariant forSpawn(ArenaWorld.EnemyKind kind, int seed, int serial) {
        if (ArenaWorld.isBoss(kind)) return ORIGINAL;
        int hash = seed ^ (serial * 0x45d9f3b) ^ (kind.ordinal() * 0x27d4eb2d);
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3b;
        int palette = Math.floorMod(hash ^ (hash >>> 16), 3);
        if (palette == 0) return ORIGINAL;
        return switch (kind) {
            case GHOUL, BRUTE -> palette == 1 ? MOSS : FROST;
            case WRAITH, CULTIST -> palette == 1 ? EMBER : DUSK;
            case BOSS, SOVEREIGN, MOURNING_BELL -> ORIGINAL;
        };
    }
}
