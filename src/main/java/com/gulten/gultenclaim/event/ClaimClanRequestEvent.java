package com.gulten.gultenclaim.event;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ClaimClanRequestEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Chunk chunk;
    private boolean handled = false;

    public ClaimClanRequestEvent(Player player, Chunk chunk) {
        this.player = player;
        this.chunk = chunk;
    }

    public Player getPlayer() {
        return player;
    }

    public Chunk getChunk() {
        return chunk;
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
