package com.gulten.gultenclaim.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ClaimClanTrustToggleEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private boolean handled = false;

    public ClaimClanTrustToggleEvent(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isHandled() {
        return handled;
    }

    public void setHandled(boolean handled) {
        this.handled = handled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
