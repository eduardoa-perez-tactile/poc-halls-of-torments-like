package com.couchcrypt.game;

/** Render-thread-owned encounter presentation and a small, allocation-free notification queue. */
public final class ArenaHudState {
    public enum Tone { INFO, SUCCESS, WARNING }

    public static final class Notice {
        public String title = "", detail = "";
        public Tone tone = Tone.INFO;
        public float remaining, lifetime;

        private void clear() {
            title = detail = "";
            tone = Tone.INFO;
            remaining = lifetime = 0f;
        }
    }

    public boolean bossVisible;
    public String bossName = "", bossPhase = "";
    public float bossHealth, bossMaxHealth = 1f;
    private final Notice[] notices = { new Notice(), new Notice(), new Notice() };
    private int count;

    /** The future encounter owner supplies real boss data; ordinary combat leaves this hidden. */
    public void setBoss(String name, String phase, float health, float max) {
        bossName = name == null ? "" : name;
        bossPhase = phase == null ? "" : phase;
        bossMaxHealth = Float.isFinite(max) ? Math.max(1f, max) : 1f;
        bossHealth = Float.isFinite(health) ? Math.max(0f, Math.min(bossMaxHealth, health)) : 0f;
        bossVisible = true;
    }

    public void clearBoss() {
        bossVisible = false;
        bossName = bossPhase = "";
        bossHealth = 0f;
        bossMaxHealth = 1f;
    }

    public void reset() {
        clearBoss();
        count = 0;
        for (Notice notice : notices) notice.clear();
    }

    /** Duplicate active/pending messages are ignored instead of extending or flooding the queue. */
    public void notify(String title, String detail, Tone tone, float seconds) {
        title = title == null ? "" : title;
        detail = detail == null ? "" : detail;
        tone = tone == null ? Tone.INFO : tone;
        for (int i = 0; i < count; i++)
            if (notices[i].title.equals(title) && notices[i].detail.equals(detail)) return;
        if (count == notices.length) {
            if (tone != Tone.WARNING) return;
            // Protect the message currently being read. Replace the oldest pending message.
            Notice recycled = notices[1];
            notices[1] = notices[2];
            notices[2] = recycled;
            count--;
        }
        Notice next = notices[count++];
        next.title = title;
        next.detail = detail;
        next.tone = tone;
        next.remaining = next.lifetime = Float.isFinite(seconds) ? Math.max(.5f, Math.min(12f, seconds)) : 3f;
    }

    /** Freeze by not calling while paused; large deltas cannot skip several unread messages. */
    public void update(float delta) {
        if (count == 0 || !Float.isFinite(delta) || delta <= 0f) return;
        Notice active = notices[0];
        active.remaining = Math.max(0f, active.remaining - Math.min(.25f, delta));
        if (active.remaining > 0f) return;
        count--;
        for (int i = 0; i < count; i++) notices[i] = notices[i + 1];
        notices[count] = active;
        active.clear();
    }

    public Notice current() { return count == 0 ? null : notices[0]; }
}
