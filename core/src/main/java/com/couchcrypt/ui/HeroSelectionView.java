package com.couchcrypt.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Scaling;
import com.couchcrypt.game.ArenaWorld;
import java.util.function.Consumer;

/** Portrait-led hero choice. Browsing is local until the host confirms a hero. */
public final class HeroSelectionView extends Table {
    private final Label chooseLabel;
    private final boolean hasRemove;
    private final RemoteButton[] heroCards = new RemoteButton[2];
    private final Button.ButtonStyle portraitNormal, portraitFocused;
    private ArenaWorld.Hero selected;

    /** Registers Warden, Ranger, Choose, Hero Info, then optional Remove Controller. */
    public HeroSelectionView(UiArt art, int slot, ArenaWorld.Hero initial,
        Runnable choose, Runnable info, Runnable remove, Consumer<RemoteButton> register) {
        selected = initial;
        hasRemove = remove != null;
        setBackground(art.panel);
        pad(34, 54, 30, 54);
        Drawable ordinary = new PortraitSurface(art.panel, art.pixel, false);
        Drawable highlighted = new PortraitSurface(art.panel, art.pixel, true);
        portraitNormal = new Button.ButtonStyle(ordinary, highlighted, ordinary);
        portraitFocused = new Button.ButtonStyle(highlighted, highlighted, highlighted);

        Table header = new Table();
        header.add(art.label("Choose a hero", art.heading, UiArt.IVORY)).expandX().left();
        header.add(art.label(slot == 0 ? "PLAYER I" : "PLAYER II", art.section, UiArt.GOLD)).right();
        add(header).growX().padBottom(24).row();

        Table heroes = new Table();
        heroes.add(heroCard(art, ArenaWorld.Hero.WARDEN, choose, register)).width(550).height(555).padRight(40);
        heroes.add(heroCard(art, ArenaWorld.Hero.RANGER, choose, register)).width(550).height(555);
        add(heroes).center().row();

        add(art.label("Attacks are automatic.", art.body, UiArt.MUTED)).padTop(16).padBottom(20).row();

        Table actions = new Table();
        RemoteButton confirm = new RemoteButton(art, choose, false);
        chooseLabel = art.label(chooseText(), art.section, UiArt.BUTTON_INK);
        chooseLabel.setFontScale(.82f);
        confirm.add(chooseLabel);
        register.accept(confirm);
        actions.add(confirm).width(hasRemove ? 510 : 620).height(78).padRight(24);
        RemoteButton details = new RemoteButton(art, info, false);
        Label infoLabel = art.label("HERO INFO", art.section, UiArt.BUTTON_INK);
        infoLabel.setFontScale(.82f);
        details.add(infoLabel);
        register.accept(details);
        actions.add(details).width(270).height(78);
        if (hasRemove) {
            RemoteButton disconnect = new RemoteButton(art, remove, false);
            disconnect.add(art.label("REMOVE CONTROLLER", art.small, UiArt.BUTTON_INK));
            register.accept(disconnect);
            actions.add(disconnect).width(330).height(78).padLeft(24);
        }
        add(actions).center().row();
        add(art.label("LEFT / RIGHT  Browse heroes     DOWN  Hero info     OK  Select     BACK  Cancel",
            art.small, UiArt.MUTED)).padTop(24).center();
    }

    private RemoteButton heroCard(UiArt art, ArenaWorld.Hero hero, Runnable choose,
        Consumer<RemoteButton> register) {
        boolean warden = hero == ArenaWorld.Hero.WARDEN;
        RemoteButton card = new RemoteButton(portraitNormal, portraitFocused,
            () -> { setHero(hero); choose.run(); });
        heroCards[warden ? 0 : 1] = card;
        card.pad(18, 20, 20, 20);
        TextureRegion source = warden ? art.wardenFace : art.rangerFace;
        Image portrait = new Image(new HeroPortraitDrawable(source, .75f));
        portrait.setScaling(Scaling.fit);
        portrait.setTouchable(Touchable.disabled);
        card.add(portrait).size(500, 375).padBottom(6).row();
        Label name = art.label(warden ? "WARDEN" : "RANGER", art.heading, UiArt.IVORY);
        name.setFontScale(.78f);
        card.add(name).padBottom(4).row();
        card.add(art.label(warden ? "MELEE / CROWD CONTROL" : "RANGED / MOBILITY", art.small, UiArt.GOLD))
            .padBottom(10).row();
        card.add(art.label(warden ? "Sweep through nearby foes." : "Strike from a distance.",
            art.body, UiArt.IVORY));
        register.accept(card);
        return card;
    }

    public ArenaWorld.Hero selectedHero() { return selected; }

    public void setHero(ArenaWorld.Hero hero) {
        if (selected == hero) return;
        selected = hero;
        chooseLabel.setText(chooseText());
    }

    /** Call after the owner's focus changes, including restored focus after Hero Info. */
    public void focusChanged(int index) {
        if (index == 0) setHero(ArenaWorld.Hero.WARDEN);
        else if (index == 1) setHero(ArenaWorld.Hero.RANGER);
        heroCards[0].focused(index == 0);
        heroCards[1].focused(index == 1);
    }

    /** Explicit remote geometry avoids diagonal jumps between the portraits and action row. */
    public int navigate(int key, int currentFocus) {
        boolean left = key == Input.Keys.LEFT;
        boolean right = key == Input.Keys.RIGHT;
        boolean up = key == Input.Keys.UP;
        boolean down = key == Input.Keys.DOWN;
        if (!left && !right && !up && !down) return -1;
        if (currentFocus < 2) {
            if (left) return 0;
            if (right) return 1;
            if (down) return 3;
            return currentFocus;
        }
        if (up) return selected == ArenaWorld.Hero.WARDEN ? 0 : 1;
        if (left) return Math.max(2, currentFocus - 1);
        if (right) return Math.min(hasRemove ? 4 : 3, currentFocus + 1);
        return currentFocus;
    }

    private String chooseText() {
        return selected == ArenaWorld.Hero.WARDEN ? "CHOOSE WARDEN" : "CHOOSE RANGER";
    }

    /** Reuses the existing skin/pixel, with a static rim that never changes content geometry. */
    private static final class PortraitSurface extends BaseDrawable {
        private final Drawable surface, pixel;
        private final boolean focused;

        PortraitSurface(Drawable surface, Drawable pixel, boolean focused) {
            super(surface);
            this.surface = surface;
            this.pixel = pixel;
            this.focused = focused;
        }

        @Override public void draw(Batch batch, float x, float y, float width, float height) {
            float previous = batch.getPackedColor();
            Color tint = batch.getColor();
            float r = tint.r, g = tint.g, b = tint.b, a = tint.a;
            batch.setColor(r * .66f, g * .72f, b * .80f, a);
            surface.draw(batch, x, y, width, height);
            if (focused) {
                batch.setColor(r, g * .77f, b * .25f, a * .24f);
                rectangle(batch, x - 2, y - 2, width + 4, height + 4, 1);
                batch.setColor(r, g * .80f, b * .30f, a);
                rectangle(batch, x + 6, y + 6, width - 12, height - 12, 3);
                batch.setColor(r, g * .91f, b * .59f, a);
                float edge = 13, length = 24, stroke = 3;
                pixel.draw(batch, x + edge, y + edge, length, stroke);
                pixel.draw(batch, x + edge, y + edge, stroke, length);
                pixel.draw(batch, x + width - edge - length, y + edge, length, stroke);
                pixel.draw(batch, x + width - edge - stroke, y + edge, stroke, length);
                pixel.draw(batch, x + edge, y + height - edge - stroke, length, stroke);
                pixel.draw(batch, x + edge, y + height - edge - length, stroke, length);
                pixel.draw(batch, x + width - edge - length, y + height - edge - stroke, length, stroke);
                pixel.draw(batch, x + width - edge - stroke, y + height - edge - length, stroke, length);
            }
            batch.setPackedColor(previous);
        }

        private void rectangle(Batch batch, float x, float y, float width, float height, float stroke) {
            pixel.draw(batch, x, y, width, stroke);
            pixel.draw(batch, x, y + height - stroke, width, stroke);
            pixel.draw(batch, x, y + stroke, stroke, height - stroke * 2);
            pixel.draw(batch, x + width - stroke, y + stroke, stroke, height - stroke * 2);
        }
    }
}
