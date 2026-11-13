package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.couchcrypt.game.ArenaWorld;

/** Ceremonial phone-choice prompt, reusing loaded heroes, fonts and menu materials. */
public final class LevelUpView extends Table {
    private final Label level;
    private final Table players = new Table();
    private final Table[] places = {new Table(), new Table()};
    private final Image[] heroes = new Image[2];
    private final Label[] names = new Label[2], statuses = new Label[2], timers = new Label[2], hints = new Label[2];
    private final TextureRegionDrawable[] portraits = new TextureRegionDrawable[2];
    private final ArenaWorld.Hero[] previousHero = new ArenaWorld.Hero[2];
    private final int[] previousStatus = {-1, -1}, previousSeconds = {-1, -1};
    private int previousLevel = -1;

    public LevelUpView(UiArt art) {
        setFillParent(true);
        setBackground(art.scrim);
        setTouchable(Touchable.disabled);
        setVisible(false);
        portraits[0] = new TextureRegionDrawable(heroRegion(art, ArenaWorld.Hero.WARDEN));
        portraits[1] = new TextureRegionDrawable(heroRegion(art, ArenaWorld.Hero.RANGER));

        Table ceremony = new Table();
        ceremony.pad(32);
        Label title = art.label("LEVEL UP", art.title, UiArt.GOLD);
        title.setFontScale(1.15f);
        ceremony.add(title).padBottom(10).row();
        Table levelRule = new Table();
        levelRule.add(rule(art, UiArt.GOLD)).width(290).height(1);
        level = art.label("", art.section, UiArt.IVORY);
        levelRule.add(level).padLeft(26).padRight(26);
        levelRule.add(rule(art, UiArt.GOLD)).width(290).height(1);
        ceremony.add(levelRule).padBottom(26).row();
        ceremony.add(art.label("CHECK YOUR PHONE", art.heading, UiArt.IVORY)).padBottom(12).row();
        ceremony.add(art.label("Combat is paused. Choose your next power.", art.body, UiArt.MUTED))
            .padBottom(28).row();

        for (int slot = 0; slot < places.length; slot++) {
            Color accent = slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO;
            Table place = places[slot];
            place.add(art.label(slot == 0 ? "PLAYER I" : "PLAYER II", art.section, accent))
                .colspan(2).padBottom(12).row();
            place.add(rule(art, accent)).colspan(2).width(520).height(1).padBottom(18).row();
            heroes[slot] = new Image(portraits[slot]);
            heroes[slot].setScaling(Scaling.fit);
            heroes[slot].setTouchable(Touchable.disabled);
            place.add(heroes[slot]).width(220).height(350).padRight(24);
            Table choice = new Table();
            names[slot] = art.label("", art.section, accent);
            choice.add(names[slot]).padBottom(20).row();
            timers[slot] = art.label("", art.title, accent);
            timers[slot].setFontScale(.9f);
            timers[slot].setAlignment(Align.center);
            choice.add(timers[slot]).width(300).height(104).padBottom(12).row();
            statuses[slot] = art.label("", art.body, UiArt.IVORY);
            choice.add(statuses[slot]).padBottom(12).row();
            hints[slot] = art.label("", art.small, UiArt.MUTED);
            choice.add(hints[slot]);
            place.add(choice).width(300);
            players.add(place).width(640).height(430).padLeft(20).padRight(20);
        }
        ceremony.add(players).row();
        ceremony.add(art.label("The first option is selected when time runs out.", art.small, UiArt.MUTED))
            .padTop(22).row();
        add(ceremony).width(1480);
    }

    /** Unchanged frames do not allocate strings, change drawables or invalidate layout. */
    public void update(ArenaWorld world) {
        if (previousLevel != world.progression.level) {
            previousLevel = world.progression.level;
            level.setText("PARTY LEVEL " + previousLevel);
        }
        for (int slot = 0; slot < places.length; slot++) {
            var player = world.players[slot];
            var offer = world.progression.offers[slot];
            if (previousHero[slot] != player.hero) {
                previousHero[slot] = player.hero;
                heroes[slot].setDrawable(portraits[player.hero == ArenaWorld.Hero.WARDEN ? 0 : 1]);
                names[slot].setText(player.hero.name());
            }
            int status = !player.active ? 0 : offer.pending ? 2 : 1;
            if (previousStatus[slot] != status) {
                previousStatus[slot] = status;
                boolean active = status != 0;
                places[slot].setVisible(active);
                players.getCell(places[slot]).width(active ? 640 : 0)
                    .padLeft(active ? 20 : 0).padRight(active ? 20 : 0);
                statuses[slot].setText(status == 2 ? "CHOOSE YOUR SKILL" : "SELECTION COMPLETE");
                statuses[slot].setColor(status == 1 ? UiArt.GREEN : UiArt.IVORY);
                hints[slot].setText(status == 2 ? "Select on your phone" : "Waiting for your companion");
                timers[slot].setColor(status == 1 ? UiArt.GREEN : slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO);
                previousSeconds[slot] = -2;
            }
            int seconds = status == 2 ? Math.max(0, Math.min(30, (int) Math.ceil(offer.secondsLeft))) : -1;
            if (previousSeconds[slot] != seconds) {
                previousSeconds[slot] = seconds;
                timers[slot].setText(seconds >= 0 ? seconds + "s" : "READY");
            }
        }
    }

    private static Image rule(UiArt art, Color color) {
        Image line = new Image(art.pixel);
        line.setColor(color.r, color.g, color.b, .5f);
        line.setTouchable(Touchable.disabled);
        return line;
    }

    private static TextureRegion heroRegion(UiArt art, ArenaWorld.Hero hero) {
        TextureRegion source = hero == ArenaWorld.Hero.WARDEN ? art.knight : art.ranger;
        float left = hero == ArenaWorld.Hero.WARDEN ? 234f / 887f : 134f / 887f;
        float width = hero == ArenaWorld.Hero.WARDEN ? 481f / 887f : 570f / 887f;
        int x = Math.round(source.getRegionWidth() * left);
        int w = Math.min(source.getRegionWidth() - x, Math.round(source.getRegionWidth() * width));
        return new TextureRegion(source, x, 0, w, source.getRegionHeight());
    }
}
