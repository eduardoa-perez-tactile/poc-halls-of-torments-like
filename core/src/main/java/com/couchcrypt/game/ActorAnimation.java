package com.couchcrypt.game;

/** Allocation-free presentation selection. Reading it never advances simulation. */
public final class ActorAnimation {
    public enum Actor { WARDEN, RANGER, GHOUL, WRAITH, BRUTE, CULTIST, SOVEREIGN }
    public enum Facing { SE, SW, NE, NW }
    public enum State { IDLE, MOVE, ATTACK, DODGE, HIT, DOWNED, WINDUP }
    public static final float SWORD_SECONDS = .18f, SHOT_SECONDS = .30f, RELEASE_SECONDS = .18f;

    private ActorAnimation() {}

    /** Dominant world axes project onto the four authored screen diagonals. */
    public static Facing facing(float x, float y, Facing previous) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || Math.abs(x) + Math.abs(y) < .0001f)
            return previous;
        if (Math.abs(x) >= Math.abs(y)) return x >= 0 ? Facing.NE : Facing.SW;
        return y >= 0 ? Facing.NW : Facing.SE;
    }

    public static Actor actor(ArenaWorld.Hero hero) {
        return hero == ArenaWorld.Hero.WARDEN ? Actor.WARDEN : Actor.RANGER;
    }

    public static Actor actor(ArenaWorld.EnemyKind kind) {
        return switch (kind) {
            case GHOUL -> Actor.GHOUL;
            case WRAITH -> Actor.WRAITH;
            case BRUTE -> Actor.BRUTE;
            case CULTIST -> Actor.CULTIST;
            case BOSS -> Actor.SOVEREIGN;
        };
    }

    public static State state(ArenaWorld.Player player) {
        if (!player.alive) return State.DOWNED;
        float actionSeconds = player.hero == ArenaWorld.Hero.WARDEN ? SWORD_SECONDS : SHOT_SECONDS;
        if (player.attackAge < actionSeconds) return State.ATTACK;
        if (player.dodgeTime > 0) return State.DODGE;
        if (player.hurtTime > 0) return State.HIT;
        return player.moving ? State.MOVE : State.IDLE;
    }

    public static Facing facing(ArenaWorld.Player player, State state) {
        if (state == State.ATTACK)
            return facing(player.attackFacingX, player.attackFacingY, Facing.SE);
        if (state == State.DODGE)
            return facing(player.dodgeFacingX(), player.dodgeFacingY(), Facing.SE);
        return facing(player.facingX, player.facingY, Facing.SE);
    }

    public static float phase(ArenaWorld.Player player, State state) {
        return switch (state) {
            case MOVE -> player.walkTime;
            case ATTACK -> player.attackAge;
            default -> 0;
        };
    }

    public static State state(ArenaWorld.Enemy enemy) {
        if ((enemy.kind == ArenaWorld.EnemyKind.CULTIST || enemy.kind == ArenaWorld.EnemyKind.BOSS)
            && enemy.releaseAge < RELEASE_SECONDS) return State.ATTACK;
        if (enemy.windup > 0) return State.WINDUP;
        // Hovering cloth can move without implying that a stunned ghost travels.
        if (enemy.kind == ArenaWorld.EnemyKind.WRAITH) return State.MOVE;
        return enemy.moving ? State.MOVE : State.IDLE;
    }

    public static Facing facing(ArenaWorld.Enemy enemy, State state) {
        if (state == State.ATTACK || state == State.WINDUP)
            return facing(enemy.castFacingX, enemy.castFacingY, Facing.SE);
        return facing(enemy.facingX, enemy.facingY, Facing.SE);
    }

    public static float phase(ArenaWorld.Enemy enemy, State state) {
        return switch (state) {
            case MOVE -> enemy.kind == ArenaWorld.EnemyKind.WRAITH ? enemy.animTime
                : enemy.kind == ArenaWorld.EnemyKind.CULTIST
                    && enemy.vx * enemy.facingX + enemy.vy * enemy.facingY < 0 ? -enemy.moveTime : enemy.moveTime;
            case ATTACK -> enemy.releaseAge;
            case WINDUP -> enemy.castAge;
            default -> 0;
        };
    }
}
