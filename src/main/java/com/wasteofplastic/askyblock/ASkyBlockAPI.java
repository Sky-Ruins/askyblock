package com.wasteofplastic.askyblock;

import com.wasteofplastic.askyblock.commands.Challenges;
import com.wasteofplastic.askyblock.panels.SetBiome;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

/**
 * Provides a programming interface
 *
 * @author tastybento
 */
public class ASkyBlockAPI {

    private static final boolean DEBUG = false;
    private static ASkyBlockAPI instance;
    private ASkyBlock plugin;

    ASkyBlockAPI(ASkyBlock plugin) {
        this.plugin = plugin;
        instance = this;
    }

    /**
     * @return the instance
     */
    public static ASkyBlockAPI getInstance() {
        if (DEBUG) {
            Bukkit.getLogger().info("DEBUG: ASkyBlock API, getInstance()");
        }
        if (instance == null) {
            new ASkyBlockAPI(ASkyBlock.getPlugin());
        }
        return instance;
    }

    /**
     * @return Map of all of the known challenges with a boolean marking
     * them as complete (true) or incomplete (false). This is a view of the
     * challenges map that only allows read operations.
     */
    public Map<String, Boolean> getChallengeStatus(UUID uuid) {
        return Collections.unmodifiableMap(plugin.getPlayers().getChallengeStatus(uuid));
    }

    /**
     * @return Map of all of the known challenges and how many times each
     * one has been completed. This is a view of the challenges
     * map that only allows read operations.
     */
    public Map<String, Integer> getChallengeTimes(UUID uuid) {
        return Collections.unmodifiableMap(plugin.getPlayers().getChallengeTimes(uuid));
    }

    public Location getHomeLocation(UUID uuid) {
        return plugin.getPlayers().getHomeLocation(uuid, 1);
    }

    /**
     * @return the last level calculated for the island or zero if none.
     * @deprecated Island level is now a long and the int value may not be accurate for very large values.
     * Use getLongIslandLevel(UUID uuid) instead.
     *
     * Returns the island level from the last time it was calculated. Note this
     * does not calculate the island level.
     */
    public int getIslandLevel(UUID uuid) {
        return (int) plugin.getPlayers().getIslandLevel(uuid);
    }

    /**
     * Returns the island level from the last time it was calculated. Note this
     * does not calculate the island level.
     *
     * @return the last level calculated for the island or zero if none.
     */
    public long getLongIslandLevel(UUID uuid) {
        if (DEBUG) {
            Bukkit.getLogger().info("DEBUG: getIslandLevel. uuid = " + uuid);
            if (plugin == null) {
                Bukkit.getLogger().info("DEBUG: plugin is null");
            } else if (plugin.getPlayers() == null) {
                Bukkit.getLogger().info("DEBUG: player cache object is null!");
            }
        }
        return plugin.getPlayers().getIslandLevel(uuid);
    }

    /**
     * Sets the player's island level. Does not calculate it and does not set the level of any team members.
     * You will need to check if the player is in a team and individually set the level of each team member.
     * This value will be overwritten if the players run the build-in level command or if the island level
     * is calculated some other way, e.g. at login or via an admin command.
     */
    public void setIslandLevel(UUID uuid, int level) {
        plugin.getPlayers().setIslandLevel(uuid, level);
    }

    /**
     * Calculates the island level. Only the fast calc is supported.
     * The island calculation runs async and fires an IslandLevelEvent when completed
     * or use getIslandLevel(uuid). See https://gist.github.com/tastybento/e81d2403c03f2fe26642
     * for example code.
     *
     * @return true if player has an island, false if not
     */
    public boolean calculateIslandLevel(UUID uuid) {
        if (plugin.getPlayers().hasIsland(uuid) || plugin.getPlayers().inTeam(uuid)) {
            new LevelCalcByChunk(plugin, uuid, null, false);
            return true;
        }
        return false;
    }

    /**
     * Provides the location of the player's island, either the team island or
     * their own
     *
     * @return Location of island
     */
    public Location getIslandLocation(UUID uuid) {
        return plugin.getPlayers().getIslandLocation(uuid);
    }

    /**
     * Returns the owner of an island from the location.
     * Uses the grid lookup and is quick
     *
     * @return UUID of owner
     */
    public UUID getOwner(Location location) {
        return plugin.getPlayers().getPlayerFromIslandLocation(location);
    }

    /**
     * Get Team Leader
     *
     * @return UUID of Team Leader or null if there is none. Use inTeam to
     * check.
     */
    public UUID getTeamLeader(UUID uuid) {
        return plugin.getPlayers().getTeamLeader(uuid);
    }

    /**
     * Get a list of team members. This is a copy and changing the return value
     * will not affect the membership.
     *
     * @return List of team members, including the player. Empty if there are
     * none.
     */
    public List<UUID> getTeamMembers(UUID uuid) {
        return new ArrayList<>(plugin.getPlayers().getMembers(uuid));
    }

    /**
     * Provides location of the player's warp sign
     *
     * @return Location of sign or null if one does not exist
     */
    public Location getWarp(UUID uuid) {
        return plugin.getWarpSignsListener().getWarp(uuid);
    }

    /**
     * Get the owner of the warp at location
     *
     * @return Returns name of player or empty string if there is no warp at
     * that spot
     */
    public String getWarpOwner(Location location) {
        return plugin.getWarpSignsListener().getWarpOwner(location);
    }

    /**
     * Status of island ownership. Team members do not have islands of their
     * own, only leaders do.
     *
     * @return true if player has an island, false if the player does not.
     */
    public boolean hasIsland(UUID uuid) {
        return plugin.getPlayers().hasIsland(uuid);
    }

    /**
     * @return true if in a team
     */
    public boolean inTeam(UUID uuid) {
        return plugin.getPlayers().inTeam(uuid);
    }

    /**
     * Determines if an island is at a location in this area location. Also
     * checks if the spawn island is in this area. Checks for bedrock within
     * limits and also looks in the file system. Quite processor intensive.
     *
     * @return true if there is an island in that location, false if not
     */
    public boolean islandAtLocation(Location location) {
        return plugin.getGrid().islandAtLocation(location);
    }

    /**
     * Checks to see if a player is trespassing on another player's island. Both
     * players must be online.
     *
     * @param owner - owner or team member of an island
     * @return true if they are on the island otherwise false.
     */
    public boolean isOnIsland(Player owner, Player target) {
        return plugin.getGrid().isOnIsland(owner, target);
    }

    /**
     * Lists all the known warps. As each player can have only one warp, the
     * player's UUID is used. It can be displayed however you like to other
     * users. This is a copy of the set and changing it will not affect the
     * actual set of warps.
     *
     * @return String set of warps
     */
    public Set<UUID> listWarps() {
        return new HashSet<>(plugin.getWarpSignsListener().listWarps());
    }

    /**
     * Forces the warp panel to update and the warp list event to fire so that
     * the warps can be sorted how you like.
     */
    public void updateWarpPanel() {
        plugin.getWarpPanel().updatePanel();
    }

    /**
     * Checks if a specific location is within the protected range of an island
     * owned by the player
     *
     * @return true if the location is on an island owner by player
     */
    public boolean locationIsOnIsland(final Player player, final Location location) {
        return plugin.getGrid().locationIsOnIsland(player, location);
    }

    /**
     * Finds out if location is within a set of island locations and returns the
     * one that is there or null if not. The islandTestLocations should be the center
     * location of an island. The check is done to see if loc is inside the protected
     * range of any of the islands given.
     *
     * @return the island location that is in the set of locations or null if
     * none
     */
    public Location locationIsOnIsland(final Set<Location> islandTestLocations, final Location loc) {
        return plugin.getGrid().locationIsOnIsland(islandTestLocations, loc);
    }

    /**
     * Checks if an online player is on their island, on a team island or on a
     * coop island
     *
     * @param player - the player who is being checked
     * @return - true if they are on their island, otherwise false
     */
    public boolean playerIsOnIsland(Player player) {
        return plugin.getGrid().playerIsOnIsland(player);
    }

    /**
     * Sets all blocks in an island to a specified biome type
     *
     * @return true if the setting was successful
     */
    public boolean setIslandBiome(Location islandLoc, Biome biomeType) {
        Island island = plugin.getGrid().getIslandAt(islandLoc);
        if (island != null) {
            new SetBiome(plugin, island, biomeType);
            return true;
        }
        return false;
    }

    /**
     * Sets a message for the player to receive next time they login
     *
     * @return true if player is offline, false if online
     */
    public boolean setMessage(UUID uuid, String message) {
        return plugin.getMessages().setMessage(uuid, message);
    }

    /**
     * Sends a message to every player in the team that is offline If the player
     * is not in a team, nothing happens.
     */
    public void tellOfflineTeam(UUID uuid, String message) {
        plugin.getMessages().tellOfflineTeam(uuid, message);
    }

    /**
     * Player is in a coop or not
     *
     * @return true if player is in a coop, otherwise false
     */
    public boolean isCoop(Player player) {
        if (CoopPlay.getInstance().getCoopIslands(player).isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Find out which coop islands player is a part of
     *
     * @return set of locations of islands or empty if none
     */
    public Set<Location> getCoopIslands(Player player) {
        return new HashSet<>(CoopPlay.getInstance().getCoopIslands(player));
    }

    /**
     * Provides spawn location
     *
     * @return Location of spawn's central point
     */
    public Location getSpawnLocation() {
        return plugin.getGrid().getSpawn().getCenter();
    }

    /**
     * Provides the spawn range
     *
     * @return spawn range
     */
    public int getSpawnRange() {
        return plugin.getGrid().getSpawn().getProtectionSize();
    }

    /**
     * Checks if a location is at spawn or not
     *
     * @return true if at spawn
     */
    public boolean isAtSpawn(Location location) {
        return plugin.getGrid().isAtSpawn(location);
    }

    /**
     * Get the island overworld
     *
     * @return the island overworld
     */
    public World getIslandWorld() {
        return ASkyBlock.getIslandWorld();
    }

    /**
     * Get the nether world
     *
     * @return the nether world
     */
    public World getNetherWorld() {
        return ASkyBlock.getNetherWorld();
    }

    /**
     * Whether the new nether is being used or not
     *
     * @return true if new nether is being used
     */
    public boolean isNewNether() {
        return Settings.newNether;
    }

    /**
     * @return Top ten list
     * @deprecated Island levels are now stored as longs, so the int value may not be accurate
     *
     * Get the top ten list
     */
    public Map<UUID, Integer> getTopTen() {
        Map<UUID, Integer> result = new HashMap<>();
        for (Entry<UUID, Long> en : TopTen.getTopTenList().entrySet()) {
            result.put(en.getKey(), en.getValue().intValue());
        }
        return result;
    }

    /**
     * Get the top ten list
     *
     * @return Top ten list
     */
    public Map<UUID, Long> getLongTopTen() {
        return new HashMap<>(TopTen.getTopTenList());
    }

    /**
     * Obtains a copy of the island object owned by uuid
     *
     * @return copy of Island object
     */
    public Island getIslandOwnedBy(UUID uuid) {
        return (Island) plugin.getGrid().getIsland(uuid).clone();
    }

    /**
     * Returns a copy of the Island object for an island at this location or null if one does not exist
     *
     * @return copy of Island object
     */
    public Island getIslandAt(Location location) {
        return plugin.getGrid().getIslandAt(location);
    }

    /**
     * @return how many islands are in the world (that the plugin knows of)
     */
    public int getIslandCount() {
        return plugin.getGrid().getIslandCount();
    }

    /**
     * Get a copy of the ownership map of islands
     *
     * @return Hashmap of owned islands with owner UUID as a key
     */
    public HashMap<UUID, Island> getOwnedIslands() {
        if (plugin.getGrid() != null) {
            Map<UUID, Island> islands = plugin.getGrid().getOwnedIslands();
            if (islands != null) {
                return new HashMap<>(islands);
            }
        }
        return new HashMap<>();

    }

    /**
     * Get name of the island owned by owner
     *
     * @return Returns the name of owner's island, or the owner's name if there is none.
     */
    public String getIslandName(UUID owner) {
        return plugin.getGrid().getIslandName(owner);
    }

    /**
     * Set the island name
     */
    public void setIslandName(UUID owner, String name) {
        plugin.getGrid().setIslandName(owner, name);
    }

    /**
     * Get all the challenges
     *
     * @return challenges per level
     */
    public LinkedHashMap<String, List<String>> getAllChallenges() {
        return Challenges.getChallengeList();
    }

    /**
     * Get the number of resets left for this player
     *
     * @return Number of resets left
     */
    public int getResetsLeft(UUID uuid) {
        return plugin.getPlayers().getResetsLeft(uuid);
    }

    /**
     * Set the number of resets left for this player
     */
    public void setResetsLeft(UUID uuid, int resets) {
        plugin.getPlayers().setResetsLeft(uuid, resets);
    }

    /**
     * Find out if this player is a team leader or not. If the player is not in a team, the result will always be false.
     *
     * @return true if the player is in a team and is the leader
     */
    public boolean isLeader(UUID uuid) {
        UUID leader = plugin.getPlayers().getTeamLeader(uuid);
        return leader != null && leader.equals(uuid);

    }

}
