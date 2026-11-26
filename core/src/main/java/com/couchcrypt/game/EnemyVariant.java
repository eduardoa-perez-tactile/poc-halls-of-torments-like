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
        int palette = Math.floorMod(spawnHash(kind, seed, serial), 3);
        if (palette == 0) return ORIGINAL;
        return switch (kind) {
            case GHOUL, BRUTE -> palette == 1 ? MOSS : FROST;
            case WRAITH, CULTIST -> palette == 1 ? EMBER : DUSK;
            case BOSS, SOVEREIGN, MOURNING_BELL -> ORIGINAL;
        };
    }

    /** Full-run learning curve: learn base species first, then introduce one rare variant at a time. */
    static EnemyVariant forSpawn(ArenaWorld.EnemyKind kind, int seed, int serial, float elapsed) {
        if (ArenaWorld.isBoss(kind)) return ORIGINAL;
        float firstAt = switch (kind) {
            case GHOUL -> 240;
            case WRAITH -> 480;
            case BRUTE -> 660;
            case CULTIST -> 840;
            default -> Float.POSITIVE_INFINITY;
        };
        float secondAt = switch (kind) {
            case GHOUL -> 360;
            case WRAITH -> 600;
            case BRUTE -> 780;
            case CULTIST -> 960;
            default -> Float.POSITIVE_INFINITY;
        };
        // Stable independent buckets prevent unlocking the second variant from replacing the first.
        int roll = Math.floorMod(spawnHash(kind, seed, serial), 10000);
        boolean solid = kind == ArenaWorld.EnemyKind.GHOUL || kind == ArenaWorld.EnemyKind.BRUTE;
        if (roll < variantWeight(elapsed, firstAt)) return solid ? MOSS : EMBER;
        if (roll >= 5000 && roll < 5000 + variantWeight(elapsed, secondAt)) return solid ? FROST : DUSK;
        return ORIGINAL;
    }

    /** Five percent on introduction, rising to twenty percent over ninety combat seconds. */
    private static int variantWeight(float elapsed, float introduction) {
        if (elapsed < introduction) return 0;
        return 500 + (int) (1500 * Math.min(1, (elapsed - introduction) / 90f));
    }

    private static int spawnHash(ArenaWorld.EnemyKind kind, int seed, int serial) {
        int hash = seed ^ (serial * 0x45d9f3b) ^ (kind.ordinal() * 0x27d4eb2d);
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3b;
        return hash ^ (hash >>> 16);
    }
}
