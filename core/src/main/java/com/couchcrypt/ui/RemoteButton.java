package com.couchcrypt.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.Actor;

public final class RemoteButton extends Button {
    private final ButtonStyle normal;
    private final ButtonStyle focused;
    private final Runnable command;
    public RemoteButton(UiArt art, Runnable command, boolean panel) {
        this(panel ? art.cardNormal : art.normal, panel ? art.cardFocused : art.focused, command);
    }
    public RemoteButton(ButtonStyle normal, ButtonStyle focused, Runnable command) {
        super(normal);
        this.normal = normal;
        this.focused = focused;
        this.command = command;
        pad(18);
        addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                event.cancel();
                if (!isDisabled()) command.run();
            }
        });
    }
    public void focused(boolean value) { setStyle(value ? focused : normal); }
    public void activate() { if (!isDisabled()) command.run(); }
}
