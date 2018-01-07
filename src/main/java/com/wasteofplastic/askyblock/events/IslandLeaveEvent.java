package com.wasteofplastic.askyblock.events;

import com.wasteofplastic.askyblock.Island;

import java.util.UUID;


/**
 * Fired when a player leaves an island team
 *
 * @author tastybento
 */
public class IslandLeaveEvent extends ASkyBlockEvent {

    /**
     * @param player
     * @param island
     */
    public IslandLeaveEvent(UUID player, Island island) {
        super(player, island);
    }

}
