package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.couchcrypt.game.ArenaWorld;
import com.couchcrypt.game.HeroCatalog;
import com.couchcrypt.game.PartyProgression;
import java.util.Locale;
import java.util.function.Consumer;

/** Informational hero reference, laid out once using existing shared art and skill icons. */
public final class CharacterInfoView {
    private CharacterInfoView() {}

    public static Table create(UiArt art, ArenaWorld.Hero hero, TextureRegion[] skillIcons,
        Runnable back, Consumer<RemoteButton> register) {
        HeroCatalog.Profile profile = HeroCatalog.profile(hero);
        Color accent = hero == ArenaWorld.Hero.WARDEN ? UiArt.GOLD : UiArt.PLAYER_TWO;
        Table root = new Table(); root.pad(48, 64, 48, 64);
        root.add(art.label("KNOW YOUR HERO", art.section, UiArt.GOLD)).padBottom(20).row();

        Table body = new Table();
        body.add(identity(art, profile, accent)).width(490).top();
        body.add(rule(art, accent)).width(1).height(710).padLeft(38).padRight(38);
        body.add(skills(art, profile, skillIcons)).width(1220).top();
        root.add(body).expandY().center().row();

        RemoteButton backButton = new RemoteButton(art, back, false);
        backButton.add(art.label("BACK TO HEROES", art.section, UiArt.BUTTON_INK));
        register.accept(backButton);
        root.add(backButton).width(480).height(78).padTop(18);
        return root;
    }

    private static Table identity(UiArt art, HeroCatalog.Profile profile, Color accent) {
        Table column = new Table(); column.top();
        Table identity = new Table();
        Image hero = new Image(heroRegion(art, profile.hero()));
        hero.setScaling(Scaling.fit); hero.setTouchable(Touchable.disabled);
        identity.add(hero).width(196).height(310).padRight(16);
        Table introduction = new Table(); introduction.left();
        Label name = art.label(profile.name().toUpperCase(Locale.ROOT), art.heading, UiArt.IVORY);
        name.setFontScale(.8f);
        introduction.add(name).left().padBottom(8).row();
        introduction.add(wrapped(art, profile.role(), art.small, accent)).width(278).left().padBottom(22).row();
        introduction.add(wrapped(art, profile.summary(), art.body, UiArt.IVORY)).width(278).left();
        identity.add(introduction).width(278).left();
        column.add(identity).width(490).padBottom(16).row();
        column.add(wrapped(art, profile.bio(), art.small, UiArt.MUTED)).width(490).padBottom(20).row();
        column.add(rule(art, accent)).width(490).height(1).padBottom(14).row();
        column.add(art.label("BASE STATS", art.small, accent)).left().padBottom(10).row();

        ArenaWorld.Hero h = profile.hero();
        Table stats = new Table();
        statPair(stats, art, "HEALTH", Integer.toString(Math.round(h.baseHealth)),
            "DAMAGE", Integer.toString(Math.round(h.baseDamage)), accent);
        statPair(stats, art, "ATTACK RATE", decimal(1f / h.attackInterval) + " /s",
            "MOVE SPEED", Math.round(h.moveSpeed / ArenaWorld.Hero.WARDEN.moveSpeed * 100) + "%", accent);
        statPair(stats, art, "RANGE", h == ArenaWorld.Hero.WARDEN ? "CLOSE" : "LONG",
            "DODGE", decimal(h.dodgeCooldown) + "s cooldown", accent);
        column.add(stats).width(490);
        return column;
    }

    private static void statPair(Table table, UiArt art, String firstLabel, String firstValue,
        String secondLabel, String secondValue, Color accent) {
        table.add(stat(art, firstLabel, firstValue, accent)).width(245).left().padBottom(12);
        table.add(stat(art, secondLabel, secondValue, accent)).width(245).left().padBottom(12).row();
    }

    private static Table stat(UiArt art, String label, String value, Color accent) {
        Table stat = new Table(); stat.left();
        stat.add(art.label(label, art.small, UiArt.MUTED)).left().row();
        stat.add(art.label(value, art.body, accent)).left().padTop(3);
        return stat;
    }

    private static Table skills(UiArt art, HeroCatalog.Profile profile, TextureRegion[] icons) {
        Table column = new Table(); column.top().left();
        column.add(art.label("AVAILABLE SKILLS", art.section, UiArt.IVORY)).left().padBottom(8).row();
        column.add(art.label("Earn shared XP. Choose your own upgrades on your phone.", art.small, UiArt.MUTED))
            .left().padBottom(24).row();
        Table roster = new Table(); roster.top().left();
        int rows = (profile.skills().size() + 1) / 2;
        // Read down each column so the catalog stays in its canonical order.
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 2; col++) {
                int index = col * rows + row;
                if (index < profile.skills().size())
                    roster.add(skill(art, profile.skills().get(index), icons)).width(590).height(102).top();
                else roster.add().width(590).height(102);
                if (col == 0) roster.add().width(40);
            }
            roster.row();
        }
        column.add(roster).left();
        return column;
    }

    private static Table skill(UiArt art, PartyProgression.Upgrade upgrade, TextureRegion[] icons) {
        Table entry = new Table(); entry.top().left();
        TextureRegion icon = icons != null && upgrade.ordinal() < icons.length ? icons[upgrade.ordinal()] : null;
        if (icon != null) {
            Image image = new Image(icon); image.setScaling(Scaling.fit); image.setTouchable(Touchable.disabled);
            entry.add(image).size(62).top().padRight(16);
        }
        Table copy = new Table(); copy.top().left();
        copy.add(art.label(upgrade.title, art.body, UiArt.IVORY)).left().padBottom(6).row();
        copy.add(wrapped(art, upgrade.description, art.small, UiArt.MUTED)).width(512).left();
        entry.add(copy).width(512).top().left();
        return entry;
    }

    private static Label wrapped(UiArt art, String text, com.badlogic.gdx.graphics.g2d.BitmapFont font, Color color) {
        Label label = art.label(text, font, color); label.setWrap(true); label.setAlignment(Align.left);
        return label;
    }

    /** Trim transparent source margins without allocating or uploading a texture. */
    private static TextureRegion heroRegion(UiArt art, ArenaWorld.Hero hero) {
        TextureRegion source = hero == ArenaWorld.Hero.WARDEN ? art.knight : art.ranger;
        float left = hero == ArenaWorld.Hero.WARDEN ? 234f / 887f : 134f / 887f;
        float width = hero == ArenaWorld.Hero.WARDEN ? 481f / 887f : 570f / 887f;
        int x = Math.round(source.getRegionWidth() * left);
        int w = Math.min(source.getRegionWidth() - x, Math.round(source.getRegionWidth() * width));
        return new TextureRegion(source, x, 0, w, source.getRegionHeight());
    }

    private static Image rule(UiArt art, Color color) {
        Image rule = new Image(art.pixel); rule.setColor(color.r, color.g, color.b, .3f);
        rule.setTouchable(Touchable.disabled); return rule;
    }

    private static String decimal(float value) { return String.format(Locale.ROOT, "%.1f", value); }
}
