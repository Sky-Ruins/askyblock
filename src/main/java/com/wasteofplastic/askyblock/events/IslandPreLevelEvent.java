package com.wasteofplastic.askyblock.events;

import com.wasteofplastic.askyblock.Island;

import org.bukkit.event.Cancellable;

import java.util.UUID;

/**
 * This event is fired after ASkyBlock calculates an island level but before it is communicated
 * to the player.
 * Use getLevel() to see the level calculated and setLevel() to change it.
 * Canceling this event will result in no change in level.
 * See IslandPostLevelEvent to cancel notifications to the player.
 *
 * @author tastybento
 */
public class IslandPreLevelEvent extends ASkyBlockEvent implements Cancellable {

    private long level;
    private boolean cancelled;
    private long points;

    /**
     * @param player
     * @param island
     * @param score
     */
    public IslandPreLevelEvent(UUID player, Island island, long score) {
        super(player, island);
        this.level = score;
    }

    /**
     * @return the level
     * @deprecated Level is stored as a long, so this may give the wrong value for very large level values
     */
    public int getLevel() {
        return (int) level;
    }

    /**
     * @param level the level to set
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * @return the level
     */
    public long getLongLevel() {
        return level;
    }

    /**
     * Set the level
     *
     * @param level
     */
    public void setLongLevel(long level) {
        this.level = level;
    }


    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * @return the number of points
     * @deprecated points are now stored as a long, so the int value may not be correct with very large values
     */
    public int getPointsToNextLevel() {
        return (int) points;
    }

    /**
     * Set the number of points the player requires to reach the next level.
     * If this is set to a negative number, the player will not be informed of
     * how many points they need to reach the next level.
     *
     * @param pointsToNextLevel
     */
    public void setPointsToNextLevel(int pointsToNextLevel) {
        this.points = pointsToNextLevel;
    }

    /**
     * @return the number of points
     */
    public long getLongPointsToNextLevel() {
        return points;
    }

    /**
     * Set the number of points the player requires to reach the next level.
     * If this is set to a negative number, the player will not be informed of
     * how many points they need to reach the next level.
     *
     * @param pointsToNextLevel
     */
    public void setLongPointsToNextLevel(long pointsToNextLevel) {
        this.points = pointsToNextLevel;
    }
}
