package com.gulten.gultenclaim.event;

import com.gulten.gultenclaim.manager.ClaimManager.ClaimedChunk;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class ClaimPermissionCheckEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ClaimedChunk claim;
    private final Chunk chunk;
    private final ActionType actionType;
    private Result result = Result.DEFAULT;

    public ClaimPermissionCheckEvent(Player player, ClaimedChunk claim, Chunk chunk, ActionType actionType) {
        this.player = player;
        this.claim = claim;
        this.chunk = chunk;
        this.actionType = actionType;
    }

    public Player getPlayer() {
        return player;
    }

    public ClaimedChunk getClaim() {
        return claim;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum Result {
        ALLOW,
        DENY,
        DEFAULT
    }

    public enum ActionType {
        GENERAL,
        BUILD,
        INTERACT,
        DAMAGE_ENTITY
    }
}
