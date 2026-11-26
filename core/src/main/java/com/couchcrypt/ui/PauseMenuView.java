package com.couchcrypt.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import java.util.function.Consumer;

/** A compact pause composition over the frozen arena, using resident painted plates. */
public final class PauseMenuView {
    public final Table root = new Table();
    public final Label message;

    public PauseMenuView(UiArt art, String status, String[] options, Runnable[] actions,
        Consumer<RemoteButton> register) {
        root.setBackground(art.panel);
        root.pad(40);

        Label title = art.label("PAUSED", art.heading, UiArt.IVORY);
        root.add(title).center().padBottom(16).row();
        message = art.label(status, art.body, UiArt.IVORY); message.setWrap(true);
        root.add(message).width(800).left().padBottom(24).row();

        // Additional disconnected-player actions fit by scaling the entire artwork,
        // preserving the same ratio instead of flattening the painted centre.
        float width = options.length <= 5 ? 600 : options.length == 6 ? 500 : 420;
        float height = width * 224f / 1024f;
        Table menu = new Table();
        for (int i = 0; i < options.length; i++) {
            RemoteButton button = new RemoteButton(art.summaryNormal, art.summaryFocused, actions[i]);
            Label caption = art.label(options[i], art.section, UiArt.BUTTON_INK);
            caption.setFontScale(.9f); button.add(caption);
            register.accept(button);
            menu.add(button).width(width).height(height).padTop(i == 0 ? 0 : 10).row();
        }
        root.add(menu).center().row();
        root.add(art.label("Combat and choice timers are frozen.", art.small, UiArt.MUTED))
            .left().padTop(16);
    }
}
