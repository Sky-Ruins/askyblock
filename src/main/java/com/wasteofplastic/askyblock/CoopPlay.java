/*******************************************************************************
 * This file is part of ASkyBlock.
 *
 *     ASkyBlock is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ASkyBlock is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ASkyBlock.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package com.wasteofplastic.askyblock;

import com.wasteofplastic.askyblock.events.CoopJoinEvent;
import com.wasteofplastic.askyblock.events.CoopLeaveEvent;
import com.wasteofplastic.askyblock.util.Util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles coop play interactions
 *
 * @author tastybento
 */
public class CoopPlay {

    private static CoopPlay instance = new CoopPlay(ASkyBlock.getPlugin());
    private static File coops;
    private Map<UUID, Map<Location, UUID>> coopPlayers = new HashMap<>();
    private ASkyBlock plugin;

    private CoopPlay(ASkyBlock plugin) {
        this.plugin = plugin;
        coops = new File(plugin.getDataFolder(), "coops.yml");
    }

    /**
     * @return the instance
     */
    public static CoopPlay getInstance() {
        return instance;
    }

    /**
     * Adds a player to an island as a coop player.
     *
     * @return true if successful, otherwise false
     */
    public boolean addCoopPlayer(Player requester, Player newPlayer) {
        // plugin.getLogger().info("DEBUG: adding coop player");
        // Find out which island this coop player is being requested to join
        Location islandLoc;
        if (plugin.getPlayers().inTeam(requester.getUniqueId())) {
            islandLoc = plugin.getPlayers().getTeamIslandLocation(requester.getUniqueId());
            // Tell the team owner
            UUID leader = plugin.getPlayers().getTeamLeader(requester.getUniqueId());
            if (Settings.onlyLeaderCanCoop && !requester.getUniqueId().equals(leader)) {
                Util.sendMessage(requester, ChatColor.RED + plugin.myLocale(requester.getUniqueId()).cannotCoop);
                return false;
            }

            plugin.getPlayers().getMembers(leader).forEach(uuid -> {
                if (uuid.equals(requester.getUniqueId())) {
                    return;
                }

                Player player = plugin.getServer().getPlayer(uuid);
                String msg = plugin.myLocale().coopInvited.replace("[name]", requester.getName())
                        .replace("[player]", newPlayer.getName());
                if (player != null) {
                    Util.sendMessage(player, ChatColor.GOLD + msg);
                } else if (uuid.equals(leader)) {
                    plugin.getMessages().setMessage(uuid, msg);
                }
            });
        } else {
            islandLoc = plugin.getPlayers().getIslandLocation(requester.getUniqueId());
        }

        Island coopIsland = plugin.getGrid().getIslandAt(islandLoc);
        if (coopIsland == null) {
            return false;
        }

        final CoopJoinEvent event = new CoopJoinEvent(newPlayer.getUniqueId(), coopIsland, requester.getUniqueId());
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        if (!coopPlayers.containsKey(newPlayer.getUniqueId())) {
            coopPlayers.get(newPlayer.getUniqueId()).put(coopIsland.getCenter(), requester.getUniqueId());
        } else {
            coopPlayers.put(newPlayer.getUniqueId(), new HashMap<Location, UUID>() {{
                put(coopIsland.getCenter(), requester.getUniqueId());
            }});
        }
        return true;
    }

    /**
     * Removes a coop player
     *
     * @return true if the player was a coop player, and false if not
     */
    public boolean removeCoopPlayer(Player requester, Player targetPlayer) {
        return removeCoopPlayer(requester, targetPlayer.getUniqueId());
    }

    public boolean removeCoopPlayer(Player requester, UUID targetPlayerUUID) {
        boolean removed = false;
        if (coopPlayers.containsKey(targetPlayerUUID)) {
            Island coopIsland = plugin.getGrid().getIsland(requester.getUniqueId());
            if (coopIsland != null) {
                final CoopLeaveEvent event = new CoopLeaveEvent(targetPlayerUUID, requester.getUniqueId(), coopIsland);
                plugin.getServer().getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    removed = coopPlayers.get(targetPlayerUUID).remove(coopIsland.getCenter()) != null;
                }
            }
        }
        return removed;
    }

    /**
     * Returns the list of islands that this player is coop on or empty if none
     *
     * @return Set of locations
     */
    public Set<Location> getCoopIslands(Player player) {
        return Optional.ofNullable(coopPlayers.get(player.getUniqueId())).orElseGet(HashMap::new).keySet();
    }

    /**
     * Gets a list of all the players that are currently coop on this island
     *
     * @return List of UUID's of players that have coop rights to the island
     */
    public List<UUID> getCoopPlayers(Location islandLoc) {
        Island coopIsland = plugin.getGrid().getIslandAt(islandLoc);
        List<UUID> result = new ArrayList<>();
        if (coopIsland != null) {
            coopPlayers.keySet().stream().filter(uuid -> coopPlayers.get(uuid).containsKey(coopIsland.getCenter())).forEach(result::add);
        }
        return result;
    }

    /**
     * Removes all coop players from an island - used when doing an island reset
     */
    public void clearAllIslandCoops(UUID player) {
        Island island = plugin.getGrid().getIsland(player);
        if (island == null) {
            return;
        }

        coopPlayers.values().forEach(coopPlayer -> {
            coopPlayer.values().forEach(uuid -> Bukkit.getPluginManager().callEvent(new CoopLeaveEvent(player, uuid, island)));
            coopPlayer.remove(island.getCenter());
        });
    }

    /**
     * Deletes all coops from player.
     * Used when player logs out.
     */
    public void clearMyCoops(Player player) {
        Island coopIsland = plugin.getGrid().getIsland(player.getUniqueId());
        if (coopPlayers.get(player.getUniqueId()) != null) {
            final AtomicBoolean notCancelled = new AtomicBoolean(false);
            final Map<Location, UUID> map = coopPlayers.get(player.getUniqueId());

            map.values().forEach(uuid -> {
                final CoopLeaveEvent event = new CoopLeaveEvent(player.getUniqueId(), uuid, coopIsland);
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    map.entrySet().removeIf(entry -> uuid.equals(entry.getValue()));
                } else {
                    notCancelled.set(true);
                }
            });

            if (notCancelled.get()) {
                coopPlayers.remove(player.getUniqueId());
            }
        }
    }

    public void saveCoops() {
        YamlConfiguration coopConfig = new YamlConfiguration();
        coopPlayers.keySet().forEach(uuid -> coopConfig.set(uuid.toString(), getMyCoops(uuid)));

        try {
            coopConfig.save(coops);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save coop.yml file!");
        }
    }

    /**
     * Gets a serialize list of all the coops for this player. Used when saving the player
     *
     * @return List of island location | uuid of invitee
     */
    private List<String> getMyCoops(UUID uuid) {
        List<String> result = new ArrayList<>();
        if (coopPlayers.containsKey(uuid)) {
            coopPlayers.get(uuid).forEach((key, value) -> result.add(Util.getStringLocation(key) + "|" + value));
        }
        return result;
    }

    public void loadCoops() {
        if (!coops.exists()) {
            return;
        }

        YamlConfiguration coopConfig = new YamlConfiguration();
        try {
            coopConfig.load(coops);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Could not load coop.yml file!");
        }
        coopConfig.getValues(false).keySet().forEach(uuid -> setMyCoops(UUID.fromString(uuid), coopConfig.getStringList(uuid)));
    }

    /**
     * Sets a player's coops from string. Used when loading a player.
     */
    private void setMyCoops(UUID uuid, List<String> coops) {
        Map<Location, UUID> temp = new HashMap<>();
        coops.stream().map(s -> s.split("\\|")).filter(strings -> strings.length == 2).forEach(strings -> {
            Island coopIsland = plugin.getGrid().getIslandAt(Util.getLocationString(strings[0]));
            if (coopIsland != null) {
                temp.put(coopIsland.getCenter(), UUID.fromString(strings[1]));
            }
        });
        coopPlayers.put(uuid, temp);
    }

    /**
     * Goes through all the known coops and removes any that were invited by
     * clearer. Returns any inventory
     * Can be used when clearer logs out or when they are kicked or leave a team
     */
    public void clearMyInvitedCoops(Player clearer) {
        Island coopIsland = plugin.getGrid().getIsland(clearer.getUniqueId());
        for (UUID uuid : coopPlayers.keySet()) {
            Iterator<Entry<Location, UUID>> en = coopPlayers.get(uuid).entrySet().iterator();
            while (en.hasNext()) {
                Entry<Location, UUID> entry = en.next();
                if (entry.getValue().equals(clearer.getUniqueId())) {
                    final CoopLeaveEvent event = new CoopLeaveEvent(uuid, clearer.getUniqueId(), coopIsland);
                    plugin.getServer().getPluginManager().callEvent(event);
                    if (!event.isCancelled()) {
                        Player target = plugin.getServer().getPlayer(uuid);
                        if (target != null) {
                            Util.sendMessage(target,
                                    ChatColor.RED + plugin.myLocale(uuid).coopRemoved.replace("[name]", clearer.getName()));
                        } else {
                            plugin.getMessages().setMessage(uuid,
                                    ChatColor.RED + plugin.myLocale(uuid).coopRemoved.replace("[name]", clearer.getName()));
                        }
                        en.remove();
                    }
                }
            }
        }
    }

    /**
     * Removes all coop players from an island - used when doing an island reset
     */
    public void clearAllIslandCoops(Location island) {
        if (island == null) {
            return;
        }

        Island coopIsland = plugin.getGrid().getIslandAt(island);
        if (coopIsland == null) {
            return;
        }

        coopPlayers.values().forEach(map -> {
            Bukkit.getPluginManager().callEvent(new CoopLeaveEvent(map.get(island), coopIsland.getOwner(), coopIsland));
            map.remove(island);
        });
    }

}