package com.couchcrypt.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Scaling;
import com.couchcrypt.game.ArenaWorld;
import java.util.function.Consumer;

/** The finished party takes center stage over the already-resident shared crypt. */
public final class ResultsSummaryView {
    public final Table root = new Table();
    public final RemoteButton replayButton, lobbyButton, detailsButton;
    private final Label replayLabel, partyStatus;
    private final boolean victory;
    private final int participants;

    public static ResultsSummaryView create(UiArt art, ArenaWorld world, Texture outcomeTitle,
        Runnable replay, Runnable lobby, Runnable details, Consumer<RemoteButton> register) {
        return new ResultsSummaryView(art, world, outcomeTitle, replay, lobby, details, register);
    }

    private ResultsSummaryView(UiArt art, ArenaWorld world, Texture outcomeTitle,
        Runnable replay, Runnable lobby, Runnable details, Consumer<RemoteButton> register) {
        victory = world.outcome == ArenaWorld.Outcome.VICTORY;
        int active = 0;
        for (ArenaWorld.Player player : world.players) if (player.active) active++;
        participants = active;

        Image title = new Image(outcomeTitle);
        title.setScaling(Scaling.fit); title.setTouchable(Touchable.disabled);
        title.setBounds(450, 838, 1020, 204); root.addActor(title);
        Label subtitle = art.label(outcomeLine(world), art.section, UiArt.IVORY);
        subtitle.setFontScale(.88f); subtitle.setAlignment(Align.center);
        subtitle.setBounds(240, 782, 1440, 48); root.addActor(subtitle);

        int shown = 0;
        for (ArenaWorld.Player player : world.players) {
            if (!player.active) continue;
            float center = active == 1 ? 960 : shown++ == 0 ? 692 : 1228;
            Color accent = player.slot == 0 ? UiArt.GOLD : UiArt.PLAYER_TWO;
            Image portrait = new Image(new HeroPortraitDrawable(
                player.hero == ArenaWorld.Hero.WARDEN ? art.wardenFace : art.rangerFace, .96f));
            portrait.setScaling(Scaling.fit); portrait.setTouchable(Touchable.disabled);
            portrait.setBounds(center - 182, 448, 364, 328); root.addActor(portrait);
            Label identity = art.label((player.slot == 0 ? "PLAYER I" : "PLAYER II") + "  /  " + player.hero.name(),
                art.section, accent);
            identity.setAlignment(Align.center);
            identity.setBounds(center - 240, 400, 480, 44); root.addActor(identity);
        }

        Table shared = new Table();
        shared.setBackground(((TextureRegionDrawable) art.pixel).tint(new Color(.018f, .025f, .032f, .72f)));
        shared.add(stat(art, formatTime(world.elapsed), victory ? "RUN TIME" : "TIME SURVIVED", 310));
        shared.add(rule(art)).width(1).height(58).pad(0, 50, 0, 50);
        shared.add(stat(art, Integer.toString(world.progression.level), "PARTY LEVEL", 310));
        shared.add(rule(art)).width(1).height(58).pad(0, 50, 0, 50);
        shared.add(stat(art, Integer.toString(world.kills), "ENEMIES SLAIN", 310));
        shared.setBounds(340, 248, 1240, 112); root.addActor(shared);

        partyStatus = art.label("", art.small, UiArt.IVORY);
        partyStatus.setAlignment(Align.center);
        partyStatus.setBounds(220, 184, 1480, 38); root.addActor(partyStatus);

        replayButton = new RemoteButton(art, replay, false);
        replayLabel = art.label("", art.section, UiArt.BUTTON_INK);
        replayLabel.setFontScale(.9f); replayButton.add(replayLabel);
        lobbyButton = action(art, "RETURN TO LOBBY", lobby);
        detailsButton = action(art, "RUN DETAILS", details);
        register.accept(replayButton); register.accept(lobbyButton); register.accept(detailsButton);
        replayButton.setBounds(236, 64, 500, 94);
        lobbyButton.setBounds(760, 64, 500, 94);
        detailsButton.setBounds(1284, 64, 400, 94);
        root.addActor(replayButton); root.addActor(lobbyButton); root.addActor(detailsButton);
    }

    /** Connection revisions update only these labels; the completed run and layout remain intact. */
    public void updateParty(int connected) {
        int count = Math.max(0, Math.min(2, connected));
        replayLabel.setText(count == 0 ? "RECONNECT PHONES"
            : (victory ? "PLAY " : "TRY ") + (count == 1 ? "SOLO AGAIN" : "CO-OP AGAIN"));
        partyStatus.setText(count == 0 ? "NO PHONES CONNECTED  /  RETURN TO THE LOBBY TO RECONNECT"
            : count == 1 && participants > 1 ? "1 / 2 PHONES CONNECTED  /  REPLAY WILL BE SOLO"
            : count == 1 ? "1 PHONE CONNECTED  /  READY FOR SOLO"
            : "2 PHONES CONNECTED  /  READY FOR CO-OP");
    }

    private static RemoteButton action(UiArt art, String caption, Runnable command) {
        RemoteButton button = new RemoteButton(art, command, false);
        Label text = art.label(caption, art.section, UiArt.BUTTON_INK);
        text.setFontScale(.9f); button.add(text); return button;
    }

    private static Table stat(UiArt art, String value, String caption, float width) {
        Table stat = new Table();
        Label number = art.label(value, art.heading, UiArt.GOLD);
        number.setAlignment(Align.center);
        stat.add(number).width(width).padBottom(8).row();
        Label label = art.label(caption, art.small, UiArt.IVORY);
        label.setFontScale(1.1f); label.setAlignment(Align.center);
        stat.add(label).width(width); return stat;
    }

    private static Image rule(UiArt art) {
        Image line = new Image(art.pixel);
        line.setColor(UiArt.GOLD.r, UiArt.GOLD.g, UiArt.GOLD.b, .35f);
        line.setTouchable(Touchable.disabled); return line;
    }

    private static String outcomeLine(ArenaWorld world) {
        if (world.outcome == ArenaWorld.Outcome.VICTORY) return "THE HOLLOW SOVEREIGN HAS FALLEN";
        if (world.bossSpawned && world.boss != null && world.boss.maxHealth > 0) {
            int remaining = Math.max(1, Math.min(100, (int) Math.ceil(world.boss.health / world.boss.maxHealth * 100)));
            return "REACHED THE SOVEREIGN  /  " + remaining + "% HEALTH REMAINING";
        }
        return "THE CRYPT CLAIMS ANOTHER SOUL";
    }

    private static String formatTime(float seconds) {
        int total = Math.max(0, (int) seconds);
        return total / 60 + ":" + (total % 60 < 10 ? "0" : "") + total % 60;
    }
}
