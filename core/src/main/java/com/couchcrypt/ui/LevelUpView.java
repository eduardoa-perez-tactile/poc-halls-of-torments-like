package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.couchcrypt.game.ArenaWorld;

/** One shared choice clock in an art-led ceremony over the frozen arena. */
public final class LevelUpView extends Table {
    private final Label title, level, timer, instruction;
    private final Table players = new Table();
    private final Label[] statuses = new Label[2];
    private final int[] previousStatus = {-1, -1};
    private final Radiance radiance;
    private int previousLevel = -1, previousSeconds = -1;
    private int previousMode = -1;

    public LevelUpView(UiArt art) {
        setFillParent(true);
        setBackground(art.scrim);
        setTouchable(Touchable.disabled);
        setVisible(false);
        Table ceremony = new Table();
        ceremony.pad(24);
        title = art.label("LEVEL UP", art.title, UiArt.GOLD);
        ceremony.add(title).padBottom(8).row();
        Table levelRule = new Table();
        levelRule.add(rule(art)).width(250).height(1);
        level = art.label("", art.section, UiArt.IVORY);
        levelRule.add(level).padLeft(26).padRight(26);
        levelRule.add(rule(art)).width(250).height(1);
        ceremony.add(levelRule).padBottom(8).row();

        Stack seal = new Stack();
        radiance = new Radiance(art.menuScene.levelUpLight);
        seal.add(radiance);
        Image crest = new Image(art.menuScene.levelUpCrest);
        crest.setScaling(Scaling.fit);
        crest.setTouchable(Touchable.disabled);
        seal.add(crest);
        Table clock = new Table();
        timer = art.label("30", art.title, UiArt.IVORY);
        timer.setAlignment(Align.center);
        clock.add(timer).width(220).height(112).row();
        clock.add(art.label("SECONDS", art.small, UiArt.GOLD));
        seal.add(clock);
        ceremony.add(seal).size(440).padBottom(8).row();

        ceremony.add(art.label("CHECK YOUR PHONE", art.heading, UiArt.IVORY)).padBottom(12).row();
        instruction = art.label("", art.body, UiArt.MUTED);
        ceremony.add(instruction).padBottom(24).row();
        for (int slot = 0; slot < statuses.length; slot++) {
            statuses[slot] = art.label("", art.body, slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
            statuses[slot].setAlignment(Align.center);
            players.add(statuses[slot]).width(380).padLeft(12).padRight(12);
        }
        ceremony.add(players).row();
        ceremony.add(art.label("The first option is selected when time runs out.", art.small, UiArt.MUTED))
            .padTop(18).row();
        add(ceremony).width(1480);
    }

    /** Unchanged frames allocate no strings and do not invalidate layout. */
    public void update(ArenaWorld world) {
        boolean loot = !world.levelUpPending() && world.lootPending();
        int mode = loot ? 1 : 0;
        if (previousMode != mode || previousLevel != world.progression.level) {
            previousMode = mode;
            previousLevel = world.progression.level;
            title.setText(loot ? "LOOT FOUND" : "LEVEL UP");
            level.setText(loot ? "EQUIPMENT REWARD" : "PARTY LEVEL " + previousLevel);
            instruction.setText(loot ? "Choose equipment or keep your current gear. Combat is paused."
                : "Choose your next power. Combat is paused.");
        }
        int seconds = Math.max(0, Math.min(30, (int) Math.ceil(loot
            ? world.equipment.choiceSecondsLeft() : world.progression.choiceSecondsLeft())));
        if (previousSeconds != seconds) {
            previousSeconds = seconds;
            timer.setText(Integer.toString(seconds));
        }
        for (int slot = 0; slot < statuses.length; slot++) {
            boolean pending = loot ? world.equipment.offers[slot].pending : world.progression.offers[slot].pending;
            int status = !world.players[slot].active ? 0 : pending ? 2 : 1;
            if (previousStatus[slot] == status) continue;
            previousStatus[slot] = status;
            boolean active = status != 0;
            statuses[slot].setVisible(active);
            players.getCell(statuses[slot]).width(active ? 380 : 0)
                .padLeft(active ? 12 : 0).padRight(active ? 12 : 0);
            statuses[slot].setText(slot == 0 ? status == 2 ? "PLAYER I: CHOOSING" : "PLAYER I: READY"
                : status == 2 ? "PLAYER II: CHOOSING" : "PLAYER II: READY");
            statuses[slot].setColor(status == 1 ? UiArt.GREEN : slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
        }
    }

    @Override public void act(float delta) {
        if (!isVisible()) return;
        super.act(delta);
        radiance.time = (radiance.time + Math.min(delta, .1f)) % 120f;
    }

    private static Image rule(UiArt art) {
        Image line = new Image(art.pixel);
        line.setColor(UiArt.GOLD.r, UiArt.GOLD.g, UiArt.GOLD.b, .5f);
        line.setTouchable(Touchable.disabled);
        return line;
    }

    /** Fixed decoration count, using one existing soft point in the scene atlas. */
    private static final class Radiance extends Actor {
        private final TextureRegion point;
        private float time;
        private Radiance(TextureRegion point) { this.point = point; }
        @Override public void draw(Batch batch, float parentAlpha) {
            Color before = batch.getColor();
            float red = before.r, green = before.g, blue = before.b, alpha = before.a;
            float cx = getX() + getWidth() * .5f, cy = getY() + getHeight() * .5f;
            float pulse = MathUtils.sin(time * 1.8f);
            batch.setColor(1f, .72f, .25f, (.16f + pulse * .025f) * parentAlpha);
            batch.draw(point, cx - 300, cy - 300, 600, 600);
            batch.setColor(1f, .8f, .4f, .14f * parentAlpha);
            for (int i = 0; i < 8; i++)
                batch.draw(point, cx - 5, cy + 110, 5, -110, 10, 178, 1, 1, i * 45f + time * 1.5f);
            for (int i = 0; i < 8; i++) {
                float angle = time * .18f + i * MathUtils.PI2 / 8f;
                float radius = 240 + MathUtils.sin(time * .7f + i) * 18;
                float size = 7 + i % 3 * 2;
                batch.setColor(1f, .85f, .5f, (.5f + MathUtils.sin(time * 1.2f + i) * .2f) * parentAlpha);
                batch.draw(point, cx + MathUtils.cos(angle) * radius - size * .5f,
                    cy + MathUtils.sin(angle) * radius - size * .5f, size, size);
            }
            batch.setColor(red, green, blue, alpha);
        }
    }
}
