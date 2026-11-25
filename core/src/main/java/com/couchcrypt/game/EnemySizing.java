package com.couchcrypt.game;

/** Shared painted-body proportions and conservative ground footprints; no texture ownership. */
public final class EnemySizing {
    public static final float HERO_REFERENCE_HEIGHT = 103.75f;
    public static final float MOSS_BULK = 1.10f, ELITE_SCALE = 1.22f;
    private static final float SOURCE_PIXEL_SCALE = 84f / 64f;

    private EnemySizing() {}

    /** Multiplier of the actor's existing pixel scale, not an absolute pixels-to-world scale. */
    public static float renderScale(ArenaWorld.EnemyKind kind, EnemyVariant variant, boolean elite) {
        if (kind == ArenaWorld.EnemyKind.MOURNING_BELL) return 1.7f;
        if (ArenaWorld.isBoss(kind)) return 2.2f;
        float normal = switch (kind) {
            case GHOUL -> HERO_REFERENCE_HEIGHT * .89f / (48f * SOURCE_PIXEL_SCALE);
            case WRAITH -> HERO_REFERENCE_HEIGHT * .96f / (50f * SOURCE_PIXEL_SCALE);
            case CULTIST -> HERO_REFERENCE_HEIGHT * 1.02f / (56f * SOURCE_PIXEL_SCALE);
            case BRUTE -> HERO_REFERENCE_HEIGHT * 1.35f / (55f * SOURCE_PIXEL_SCALE);
            default -> throw new IllegalArgumentException("Unsupported enemy size: " + kind);
        };
        // Elite bulk replaces the Moss presentation modifier rather than stacking it.
        return normal * (elite ? ELITE_SCALE : variant == EnemyVariant.MOSS ? MOSS_BULK : 1f);
    }

    /** Ground circles fit thin painted bodies instead of treating their full height as width. */
    public static float groundRadius(ArenaWorld.EnemyKind kind, EnemyVariant variant, boolean elite) {
        if (ArenaWorld.isBoss(kind)) return 54f;
        float base = switch (kind) {
            case GHOUL -> 20f;
            case WRAITH, CULTIST -> 14f;
            case BRUTE -> 31f / 1.4f;
            default -> throw new IllegalArgumentException("Unsupported enemy footprint: " + kind);
        };
        return base * renderScale(kind, variant, elite);
    }
}
