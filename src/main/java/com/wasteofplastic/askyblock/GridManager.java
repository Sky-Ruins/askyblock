package com.wasteofplastic.askyblock;

import com.google.common.base.Preconditions;
import com.wasteofplastic.askyblock.Island.SettingsFlag;
import com.wasteofplastic.askyblock.events.IslandChangeOwnerEvent;
import com.wasteofplastic.askyblock.util.Util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.material.SimpleAttachableMaterialData;
import org.bukkit.material.TrapDoor;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * This class manages the island islandGrid. It knows where every island is, and
 * where new
 * ones should go. It can handle any size of island or protection size
 * The islandGrid is stored in a YML file.
 *
 * @author tastybento
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class GridManager {

    private static final String SETTINGS_KEY = "settingskey";
    private static final String ISLANDS_FILENAME = "islands.yml";
    private static final String ISLANDNAMES_FILENAME = "islandnames.yml";
    private ASkyBlock plugin;
    private TreeMap<Integer, TreeMap<Integer, Island>> islandGrid = new TreeMap<>();
    private Map<UUID, Island> ownershipMap = new HashMap<>();
    private Island spawn;
    private File islandNameFile;
    private YamlConfiguration islandNames = new YamlConfiguration();

    public GridManager(ASkyBlock plugin) {
        this.plugin = plugin;
        loadGrid();
    }

    /**
     * Checks if this location is safe for a player to teleport to. Used by
     * warps and boat exits Unsafe is any liquid or air and also if there's no
     * space
     *
     * @param l - Location to be checked
     * @return true if safe, otherwise false
     */
    public static boolean isSafeLocation(final Location l) {
        if (l == null) {
            return false;
        }
        // TODO: improve the safe location finding.
        final Block ground = l.getBlock().getRelative(BlockFace.DOWN);
        final Block space1 = l.getBlock();
        final Block space2 = l.getBlock().getRelative(BlockFace.UP);
        if (space1.getType() == Material.PORTAL || ground.getType() == Material.PORTAL || space2.getType() == Material.PORTAL
                || space1.getType() == Material.ENDER_PORTAL || ground.getType() == Material.ENDER_PORTAL
                || space2.getType() == Material.ENDER_PORTAL) {
            return false;
        }

        if (ground.getType() == Material.AIR) {
            return false;
        }

        if (ground.isLiquid() || space1.isLiquid() || space2.isLiquid()) {
            if (Settings.acidDamage > 0D) {
                return false;
            } else if (ground.getType() == Material.STATIONARY_LAVA || ground.getType() == Material.LAVA
                    || space1.getType() == Material.STATIONARY_LAVA || space1.getType() == Material.LAVA
                    || space2.getType() == Material.STATIONARY_LAVA || space2.getType() == Material.LAVA) {
                return false;
            }
        }

        MaterialData md = ground.getState().getData();
        if (md instanceof SimpleAttachableMaterialData) {
            if (md instanceof TrapDoor) {
                TrapDoor trapDoor = (TrapDoor) md;
                if (trapDoor.isOpen()) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return ground.getType() != Material.CACTUS && ground.getType() != Material.BOAT && ground.getType() != Material.FENCE
                && ground.getType() != Material.NETHER_FENCE && ground.getType() != Material.SIGN_POST
                && ground.getType() != Material.WALL_SIGN && (!space1.getType().isSolid() || space1.getType() == Material.SIGN_POST
                || space1.getType() == Material.WALL_SIGN) && (!space2.getType().isSolid() || space2.getType() != Material.SIGN_POST
                || space2.getType() != Material.WALL_SIGN);

    }

    private void loadGrid() {
        plugin.getLogger().info("Loading island grid...");
        islandGrid.clear();
        islandNameFile = new File(plugin.getDataFolder(), ISLANDNAMES_FILENAME);
        if (!islandNameFile.exists()) {
            try {
                islandNameFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create " + ISLANDNAMES_FILENAME + "!");
            }
        }

        try {
            islandNames.load(islandNameFile);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Could not load " + ISLANDNAMES_FILENAME);
        }

        File islandFile = new File(plugin.getDataFolder(), ISLANDS_FILENAME);
        if (!islandFile.exists()) {
            plugin.getLogger().info(ISLANDS_FILENAME + " does not exist. Creating...");
            convert();
            plugin.getLogger().info(ISLANDS_FILENAME + " created.");
        } else {
            plugin.getLogger().info("Loading " + ISLANDS_FILENAME);
            YamlConfiguration islandYaml = new YamlConfiguration();
            try {
                islandYaml.load(islandFile);
                List<String> islandList;
                if (islandYaml.contains(Settings.worldName)) {
                    List<String> settingsKey = islandYaml.getStringList(SETTINGS_KEY);

                    if (islandYaml.contains("spawn")) {
                        Location spawnLoc = Util.getLocationString(islandYaml.getString("spawn.location"));

                        if (spawnLoc != null && spawnLoc.getWorld() != null && spawnLoc.getWorld().equals(ASkyBlock.getIslandWorld())) {
                            Location spawnPoint = Util.getLocationString(islandYaml.getString("spawn.spawnpoint"));

                            int range = islandYaml.getInt("spawn.range", Settings.islandProtectionRange);
                            if (range < 0) {
                                range = Settings.islandProtectionRange;
                            }

                            String spawnSettings = islandYaml.getString("spawn.settings");
                            Island newSpawn = new Island(plugin, spawnLoc.getBlockX(), spawnLoc.getBlockZ());
                            newSpawn.setSpawn(true);
                            if (spawnPoint != null) {
                                newSpawn.setSpawnPoint(spawnPoint);
                            }

                            newSpawn.setProtectionSize(range);
                            newSpawn.setSettings(spawnSettings, settingsKey);
                            spawn = newSpawn;
                        }
                    }

                    islandList = islandYaml.getStringList(Settings.worldName);
                    for (String island : islandList) {
                        Island newIsland = addIsland(island, settingsKey);
                        if (newIsland.getOwner() != null) {
                            ownershipMap.put(newIsland.getOwner(), newIsland);
                        }
                        if (newIsland.isSpawn()) {
                            spawn = newIsland;
                        }
                    }
                } else {
                    plugin.getLogger().severe("Could not find any islands for this world. World name in config.yml is probably wrong.");
                    plugin.getLogger().severe("Making backup of " + ISLANDS_FILENAME + ". Correct world name and then replace "
                            + ISLANDS_FILENAME);
                    File rename = new File(plugin.getDataFolder(), "islands_backup.yml");
                    islandFile.renameTo(rename);
                }
            } catch (IOException | InvalidConfigurationException e) {
                plugin.getLogger().severe("Could not load " + ISLANDS_FILENAME);
            }
        }
    }

    /**
     * Provides confirmation that the island is on the grid lines
     */
    public boolean onGrid(Location loc) {
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return onGrid(x, z);
    }

    private boolean onGrid(int x, int z) {
        return (x - Settings.islandXOffset) % Settings.islandDistance == 0 && (z - Settings.islandZOffset) % Settings.islandDistance == 0;
    }

    /**
     * Converts from the old version where islands were stored in an island
     * folder.
     * Did not work for large installations.
     */
    private void convert() {
        final File spawnFile = new File(plugin.getDataFolder(), "spawn.yml");
        if (spawnFile.exists()) {
            YamlConfiguration spawn = new YamlConfiguration();
            try {
                spawn.load(spawnFile);
                int range = spawn.getInt("spawn.range");
                Location spawnLoc = Util.getLocationString(spawn.getString("spawn.bedrock", ""));
                if (spawnLoc != null && onGrid(spawnLoc)) {
                    Island newIsland = addIsland(spawnLoc.getBlockX(), spawnLoc.getBlockZ());
                    setSpawn(newIsland);
                    newIsland.setProtectionSize(range);
                } else {
                    plugin.getLogger().severe("Spawn could not be imported! Location " + spawnLoc);
                    plugin.getLogger().severe("Go to the spawn island and set it manually");
                }
            } catch (InvalidConfigurationException | IOException e) {
                plugin.getLogger().severe("Spawn could not be imported! File could not load.");
            }
        }

        final File playerFolder = new File(plugin.getDataFolder() + File.separator + "players");
        final File quarantineFolder = new File(plugin.getDataFolder() + File.separator + "quarantine");
        YamlConfiguration playerFile = new YamlConfiguration();
        int noisland = 0, inTeam = 0, count = 0;

        File[] playerFiles = Preconditions.checkNotNull(playerFolder.listFiles(), "player files");

        if (playerFolder.exists() && playerFiles.length > 0) {
            plugin.getLogger().warning("Reading player folder...");
            if (playerFiles.length > 5000) {
                plugin.getLogger().warning("This could take some time with a large number of islands...");
            }

            for (File f : playerFiles) {
                String fileName = f.getName();
                if (fileName.endsWith(".yml")) {
                    try {
                        playerFile.load(f);
                        boolean hasIsland = playerFile.getBoolean("hasIsland", false);
                        if (hasIsland) {
                            String islandLocation = playerFile.getString("islandLocation");
                            if (islandLocation.isEmpty()) {
                                plugin.getLogger().severe("Problem with " + fileName);
                                plugin.getLogger().severe("Owner :" + playerFile.getString("playerName", "Unknown"));
                                plugin.getLogger().severe("Player file says they have an island, but there is no location.");

                                if (!quarantineFolder.exists()) {
                                    quarantineFolder.mkdir();
                                }

                                plugin.getLogger().severe("Moving " + f.getName() + " to " + quarantineFolder.getName());
                                File rename = new File(quarantineFolder, f.getName());
                                f.renameTo(rename);
                            } else {
                                Location islandLoc = Util.getLocationString(islandLocation);
                                if (islandLoc != null) {
                                    Island island = getIslandAt(islandLoc);

                                    if (island != null) {
                                        plugin.getLogger().severe("Problem with " + fileName);
                                        plugin.getLogger().severe("Owner :" + playerFile.getString("playerName", "Unknown"));
                                        plugin.getLogger().severe("This island location already exists and is already imported");

                                        if (island.getUpdatedDate() > f.lastModified()) {
                                            plugin.getLogger().severe("Previous file is more recent so keeping it.");
                                            if (!quarantineFolder.exists()) {
                                                quarantineFolder.mkdir();
                                            }
                                            plugin.getLogger().severe("Moving " + (playerFile.getString("playerName", "Unknown"))
                                                    + "'s file (" + f.getName() + ") to " + quarantineFolder.getName());
                                            File rename = new File(quarantineFolder, f.getName());
                                            f.renameTo(rename);
                                        } else {
                                            plugin.getLogger().severe(playerFile.getString("playerName", "UNK") + "'s file is more recent");
                                            File oldFile = new File(playerFolder, island.getOwner().toString() + ".yml");
                                            File rename = new File(quarantineFolder, oldFile.getName());

                                            if (!quarantineFolder.exists()) {
                                                quarantineFolder.mkdir();
                                            }
                                            plugin.getLogger().severe("Moving previous file (" + oldFile.getName() + ") to "
                                                    + quarantineFolder.getName());
                                            oldFile.renameTo(rename);
                                            deleteIsland(islandLoc);
                                            island = null;
                                        }
                                    }
                                    if (island == null) {
                                        if (!onGrid(islandLoc)) {
                                            plugin.getLogger().severe("Problem with " + fileName);
                                            plugin.getLogger().severe("Owner :" + playerFile.getString("playerName", "Unknown"));
                                            plugin.getLogger().severe("Island is not on grid lines! " + islandLoc);
                                        }

                                        String ownerString = fileName.substring(0, fileName.length() - 4);
                                        UUID owner = UUID.fromString(ownerString);
                                        Island newIsland = addIsland(islandLoc.getBlockX(), islandLoc.getBlockZ(), owner);
                                        ownershipMap.put(owner, newIsland);
                                        newIsland.setUpdatedDate(f.lastModified());

                                        if (count++ % 1000 == 0) {
                                            plugin.getLogger().info("Converted " + count + " islands");
                                        }

                                        int islandLevel = playerFile.getInt("islandLevel", 0);
                                        String teamLeaderUUID = playerFile.getString("teamLeader", "");
                                        if (islandLevel > 0) {
                                            if (!playerFile.getBoolean("hasTeam")) {
                                                TopTen.topTenAddEntry(owner, islandLevel);
                                            } else if (!teamLeaderUUID.isEmpty() && teamLeaderUUID.equals(ownerString)) {
                                                TopTen.topTenAddEntry(owner, islandLevel);
                                            }
                                        }

                                        String islandInfo = playerFile.getString("islandInfo", "");
                                        if (!islandInfo.isEmpty()) {
                                            String[] split = islandInfo.split(":");

                                            newIsland.setLocked(false);
                                            if (split.length > 6 && split[6].equalsIgnoreCase("true")) {
                                                newIsland.setLocked(true);
                                            }
                                            newIsland.setPurgeProtected(false);
                                            if (split.length > 7 && split[7].equalsIgnoreCase("true")) {
                                                newIsland.setPurgeProtected(true);
                                            }
                                            if (!split[5].equals("null") && split[5].equals("spawn")) {
                                                newIsland.setSpawn(true);
                                                if (split.length > 8) {
                                                    Location spawnPoint = Util.getLocationString(islandInfo
                                                            .substring(islandInfo.indexOf(":SP:") + 4));
                                                    newIsland.setSpawnPoint(spawnPoint);
                                                }

                                            }

                                            if (!newIsland.isSpawn() && split.length > 8 && split[8].length() == 29) {
                                                int index = 0;
                                                for (SettingsFlag flag : SettingsFlag.values()) {
                                                    if (index < split[8].length()) {
                                                        newIsland.setIgsFlag(flag, split[8].charAt(index++) == '1');
                                                    }
                                                }
                                            }

                                        }
                                    }
                                } else {
                                    plugin.getLogger().severe("Problem with " + fileName);
                                    plugin.getLogger().severe("Owner :" + playerFile.getString("playerName", "Unknown"));
                                    plugin.getLogger().severe("The world for this file does not exist!");
                                }
                            }
                        } else {
                            noisland++;
                            if (playerFile.getBoolean("hasTeam", false)) {
                                inTeam++;
                            }
                        }

                    } catch (InvalidConfigurationException | IOException e) {
                        plugin.getLogger().severe("Problem with " + fileName);
                    }
                }
            }
            plugin.getLogger().info("Converted " + count + " islands from player's folder");
            plugin.getLogger().info(noisland + " have no island, of which " + inTeam + " are in a team.");
            plugin.getLogger().info((noisland - inTeam) + " are in the system, but have no island or team");
        }
        TopTen.topTenSave();

        int count2 = 0;
        final File islandFolder = new File(plugin.getDataFolder() + File.separator + "islands");
        File[] islands = Preconditions.checkNotNull(islandFolder.listFiles(), "island files");
        if (islandFolder.exists() && islands.length > 0) {
            plugin.getLogger().warning("Reading island folder...");
            if (islands.length > 5000) {
                plugin.getLogger().warning("This could take some time with a large number of islands...");
            }

            for (File f : islands) {
                String fileName = f.getName();
                int comma = fileName.indexOf(",");
                if (fileName.endsWith(".yml") && comma != -1) {
                    int x = Integer.parseInt(fileName.substring(0, comma));
                    int z = Integer.parseInt(fileName.substring(comma + 1, fileName.indexOf(".")));
                    if (!onGrid(x, z)) {
                        plugin.getLogger().severe("Island is not on grid lines! " + x + "," + z + " skipping...");
                    } else if (getIslandAt(x, z) == null) {
                        addIsland(x, z);
                        if (count2++ % 1000 == 0) {
                            plugin.getLogger().info("Converted " + count + " islands");
                        }
                    }
                }
            }
        }
        plugin.getLogger().info("Converted " + count2 + " islands from island folder");
        plugin.getLogger().info("Total " + (count + count2) + " islands converted.");
        saveGrid();
    }

    /**
     * Saves the grid asynchronously
     */
    public void saveGrid() {
        saveGrid(true);
    }

    /**
     * Saves the grid. Option to save sync or async.
     * Async cannot be used when disabling the plugin
     */
    public void saveGrid(boolean async) {
        final File islandFile = new File(plugin.getDataFolder(), ISLANDS_FILENAME);
        final YamlConfiguration islandYaml = new YamlConfiguration();

        List<String> islandSettings = new ArrayList<>();
        for (SettingsFlag flag : SettingsFlag.values()) {
            islandSettings.add(flag.toString());
        }

        if (getSpawn() != null) {
            islandYaml.set("spawn.location", Util.getStringLocation(getSpawn().getCenter()));
            islandYaml.set("spawn.spawnpoint", Util.getStringLocation(getSpawn().getSpawnPoint()));
            islandYaml.set("spawn.range", getSpawn().getProtectionSize());
            islandYaml.set("spawn.settings", getSpawn().getSettings());
        }

        List<String> islandList = new ArrayList<>();
        for (TreeMap<Integer, Island> integerIslandTreeMap : islandGrid.values()) {
            for (Island island : integerIslandTreeMap.values()) {
                if (!island.isSpawn()) {
                    islandList.add(island.save());
                }
            }
        }

        islandYaml.set(Settings.worldName, islandList);
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    islandYaml.save(islandFile);
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not save " + ISLANDS_FILENAME + "!");
                    //e.printStackTrace();
                }
            });
        } else {
            try {
                islandYaml.save(islandFile);
            } catch (Exception e) {
                plugin.getLogger().severe("Could not save " + ISLANDS_FILENAME + "! " + e.getMessage());
            }
        }
        // Save any island names
        if (islandNames != null) {
            try {
                islandNames.save(islandNameFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save islandnames.yml! " + e.getMessage());
            }
        }
    }

    /**
     * Returns the owner of the island at location or null if there is none
     *
     * @return UUID of owner
     */
    public UUID getOwnerOfIslandAt(Location location) {
        Island island = getIslandAt(location);
        if (island != null) {
            return island.getOwner();
        }
        return null;
    }

    /**
     * Returns the island at the location or null if there is none.
     * This includes the full island space, not just the protected area
     *
     * @return PlayerIsland object
     */
    public Island getIslandAt(Location location) {
        if (location == null) {
            return null;
        }
        // World check
        if (!inWorld(location)) {
            return null;
        }
        // Check if it is spawn
        if (spawn != null && spawn.onIsland(location)) {
            return spawn;
        }
        return getIslandAt(location.getBlockX(), location.getBlockZ());
    }

    /**
     * Determines if a location is in the island world or not or
     * in the new nether if it is activated
     *
     * @return true if in the island world
     */
    private boolean inWorld(Location loc) {
        return loc.getWorld().equals(ASkyBlock.getIslandWorld())
                || Settings.createNether && Settings.newNether && ASkyBlock.getNetherWorld() != null
                && loc.getWorld().equals(ASkyBlock.getNetherWorld());
    }

    /**
     * Returns the island at the x,z location or null if there is none.
     * This includes the full island space, not just the protected area.
     *
     * @return PlayerIsland or null
     */
    public Island getIslandAt(int x, int z) {
        Entry<Integer, TreeMap<Integer, Island>> en = islandGrid.floorEntry(x);
        if (en != null) {
            Entry<Integer, Island> ent = en.getValue().floorEntry(z);
            if (ent != null) {
                Island island = ent.getValue();
                if (island.inIslandSpace(x, z)) {
                    return island;
                }
            }
        }
        return null;
    }

    // islandGrid manipulation methods

    /**
     * Adds island to the grid using the stored information
     */
    private Island addIsland(String islandSerialized, List<String> settingsKey) {
        // plugin.getLogger().info("DEBUG: adding island " + islandSerialized);
        Island newIsland = new Island(plugin, islandSerialized, settingsKey);
        addToGrids(newIsland);
        return newIsland;
    }

    /**
     * Deletes any island owned by owner from the grid. Does not actually remove the island
     * from the world. Used for cleaning up issues such as mismatches between player files
     * and island.yml
     */
    public void deleteIslandOwner(UUID owner) {
        if (owner != null && ownershipMap.containsKey(owner)) {
            Island island = ownershipMap.get(owner);
            if (island != null) {
                island.setOwner(null);
            }
            ownershipMap.remove(owner);
        }
    }

    /**
     * Gets island by owner's UUID. Just because the island does not exist in
     * this map
     * does not mean it does not exist in this world, due to legacy island
     * support
     * Will return the island that this player is a member of if a team player
     *
     * @return island object or null if it does not exist in the list
     */
    public Island getIsland(UUID owner) {
        if (owner != null) {
            if (ownershipMap.containsKey(owner)) {
                return ownershipMap.get(owner);
            }
            // Try and get team islands
            UUID leader = plugin.getPlayers().getTeamLeader(owner);
            if (leader != null && ownershipMap.containsKey(leader)) {
                return ownershipMap.get(leader);
            }
        }
        return null;
    }

    /**
     * @return the ownershipMap
     */
    public Map<UUID, Island> getOwnershipMap() {
        return ownershipMap;
    }

    public void deleteSpawn() {
        deleteIsland(spawn.getCenter());
        this.spawn = null;

    }

    /**
     * Removes the island at location loc from the grid and removes the player
     * from the ownership map
     */
    public void deleteIsland(Location loc) {
        Island island = getIslandAt(loc);
        if (island != null) {
            UUID owner = island.getOwner();
            int x = island.getMinX();
            int z = island.getMinZ();
            if (islandGrid.containsKey(x)) {
                TreeMap<Integer, Island> zEntry = islandGrid.get(x);
                if (zEntry.containsKey(z)) {
                    Island deletedIsland = zEntry.get(z);
                    deletedIsland.setOwner(null);
                    deletedIsland.setLocked(false);
                    zEntry.remove(z);
                    islandGrid.put(x, zEntry);
                }
            }

            if (owner != null && ownershipMap.containsKey(owner)) {
                if (ownershipMap.get(owner).equals(island)) {
                    ownershipMap.remove(owner);
                }
            }
        }

    }

    /**
     * Determines if an island is at a location in this area
     * location. Also checks if the spawn island is in this area.
     * Used for creating new islands ONLY
     *
     * @return true if found, otherwise false
     */
    public boolean islandAtLocation(Location loc) {
        if (loc == null) {
            return true;
        }

        loc = getClosestIsland(loc);
        if (getIslandAt(loc) != null) {
            return true;
        }

        final int px = loc.getBlockX(), pz = loc.getBlockZ();

        Island spawn = getSpawn();
        if (spawn != null && spawn.getProtectionSize() > spawn.getIslandDistance()
                && (Math.abs(px - spawn.getCenter().getBlockX()) < ((spawn.getProtectionSize() + Settings.islandDistance) / 2)
                && Math.abs(pz - spawn.getCenter().getBlockZ()) < ((spawn.getProtectionSize() + Settings.islandDistance) / 2))) {
            return true;
        }
        if (!Settings.useOwnGenerator) {
            if (!loc.getBlock().isEmpty() && !loc.getBlock().isLiquid()) {
                plugin.getLogger().info("Found solid block at island height - adding to " + ISLANDS_FILENAME + " " + px + "," + pz);
                addIsland(px, pz);
                return true;
            }
            for (int x = -5; x <= 5; x++) {
                for (int y = 10; y <= 255; y++) {
                    for (int z = -5; z <= 5; z++) {
                        if (!loc.getWorld().getBlockAt(x + px, y, z + pz).isEmpty()
                                && !loc.getWorld().getBlockAt(x + px, y, z + pz).isLiquid()) {
                            plugin.getLogger().info("Solid block found during long search - adding to " + ISLANDS_FILENAME + " " + px + ","
                                    + pz);
                            addIsland(px, pz);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Adds an island to the islandGrid with the CENTER point x,z
     */
    public Island addIsland(int x, int z) {
        return addIsland(x, z, null);
    }

    /**
     * Adds an island to the islandGrid with the center point x,z owner UUID
     */
    public Island addIsland(int x, int z, UUID owner) {
        if (ownershipMap.containsKey(owner)) {
            Island island = ownershipMap.get(owner);
            if (island.getCenter().getBlockX() != x || island.getCenter().getBlockZ() != z) {
                island.setOwner(null);
                ownershipMap.remove(owner);
            } else {
                addToGrids(island);
                return island;
            }
        }
        Island newIsland = new Island(plugin, x, z, owner);
        addToGrids(newIsland);
        return newIsland;
    }

    /**
     * Adds an island to the grid register
     */
    private void addToGrids(Island newIsland) {
        if (newIsland.getOwner() != null) {
            ownershipMap.put(newIsland.getOwner(), newIsland);
        }
        if (islandGrid.containsKey(newIsland.getMinX())) {
            TreeMap<Integer, Island> zEntry = islandGrid.get(newIsland.getMinX());
            if (zEntry.containsKey(newIsland.getMinZ())) {
                Island conflict = islandGrid.get(newIsland.getMinX()).get(newIsland.getMinZ());
                plugin.getLogger().warning("*** Duplicate or overlapping islands! ***");
                plugin.getLogger().warning("Island at (" + newIsland.getCenter().getBlockX() + ", " + newIsland.getCenter().getBlockZ()
                        + ") conflicts with (" + conflict.getCenter().getBlockX() + ", " + conflict.getCenter().getBlockZ() + ")");
                if (conflict.getOwner() != null) {
                    plugin.getLogger().warning("Accepted island is owned by " + plugin.getPlayers().getName(conflict.getOwner()));
                    plugin.getLogger().warning(conflict.getOwner().toString() + ".yml");
                } else {
                    plugin.getLogger().warning("Accepted island is unowned.");
                }
                if (newIsland.getOwner() != null) {
                    plugin.getLogger().warning("Denied island is owned by " + plugin.getPlayers().getName(newIsland.getOwner()));
                    plugin.getLogger().warning(newIsland.getOwner().toString() + ".yml");
                } else {
                    plugin.getLogger().warning("Denied island is unowned and was just found in the islands folder. Skipping it...");
                }
                plugin.getLogger().warning("Recommend that the denied player file is deleted otherwise weird things can happen.");
            } else {
                zEntry.put(newIsland.getMinZ(), newIsland);
                islandGrid.put(newIsland.getMinX(), zEntry);
            }
        } else {
            TreeMap<Integer, Island> zEntry = new TreeMap<>();
            zEntry.put(newIsland.getMinZ(), newIsland);
            islandGrid.put(newIsland.getMinX(), zEntry);
        }
    }

    /**
     * @return the spawn or null if spawn does not exist
     */
    public Island getSpawn() {
        return spawn;
    }

    /**
     * @param spawn the spawn to set
     */
    public void setSpawn(Island spawn) {
        // plugin.getLogger().info("DEBUG: Spawn set");
        spawn.setSpawn(true);
        spawn.setProtectionSize(spawn.getIslandDistance());
        this.spawn = spawn;
    }

    /**
     * This returns the coordinate of where an island should be on the grid.
     *
     * @return Location of closest island
     */
    public Location getClosestIsland(Location location) {
        long x = Math.round(location.getBlockX() / Settings.islandDistance) * Settings.islandDistance + Settings.islandXOffset;
        long z = Math.round(location.getBlockZ() / Settings.islandDistance) * Settings.islandDistance + Settings.islandZOffset;
        long y = Settings.islandHeight;
        return new Location(location.getWorld(), x, y, z);
    }

    /**
     * This is a generic scan that can work in the overworld or the nether
     *
     * @param l - location around which to scan
     * @param i - the range to scan for a location < 0 means the full island.
     * @return - safe location, or null if none can be found
     */
    public Location bigScan(Location l, int i) {
        final int height, depth;
        if (i > 0) {
            height = i;
            depth = i;
        } else {
            Island island = plugin.getGrid().getIslandAt(l);
            if (island == null) {
                return null;
            }
            i = island.getProtectionSize();
            height = l.getWorld().getMaxHeight() - l.getBlockY();
            depth = l.getBlockY();
        }

        int minXradius = 0, maxXradius = 0;
        int minYradius = 0, maxYradius = 0;
        int minZradius = 0, maxZradius = 0;

        do {
            int minX = l.getBlockX() - minXradius, maxX = l.getBlockX() + maxXradius;
            int minZ = l.getBlockZ() - minZradius, maxZ = l.getBlockZ() + maxZradius;
            int minY = l.getBlockY() - minYradius, maxY = l.getBlockY() + maxYradius;

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        if (!((x > minX && x < maxX) && (z > minZ && z < maxZ) && (y > minY && y < maxY))) {
                            Location ultimate = new Location(l.getWorld(), x + 0.5D, y, z + 0.5D);
                            if (isSafeLocation(ultimate)) {
                                return ultimate;
                            }
                        }
                    }
                }
            }

            if (minXradius < i) {
                minXradius++;
            }
            if (maxXradius < i) {
                maxXradius++;
            }
            if (minZradius < i) {
                minZradius++;
            }
            if (maxZradius < i) {
                maxZradius++;
            }
            if (minYradius < depth) {
                minYradius++;
            }
            if (maxYradius < height) {
                maxYradius++;
            }
        } while (minXradius < i || maxXradius < i || minZradius < i || maxZradius < i || minYradius < depth || maxYradius < height);
        return null;
    }

    /**
     * Sets the home location based on where the player is now
     */
    public void homeSet(final Player player) {
        homeSet(player, 1);
    }

    /**
     * Sets the numbered home location based on where the player is now
     */
    public void homeSet(Player player, int number) {
        if (!player.getWorld().equals(plugin.getPlayers().getIslandLocation(player.getUniqueId()).getWorld())) {
            Util.sendMessage(player, ChatColor.RED + plugin.myLocale(player.getUniqueId()).setHomeerrorNotOnIsland);
            return;
        }
        // Check if player is on island, ignore coops
        if (!plugin.getGrid().playerIsOnIsland(player, false)) {
            Util.sendMessage(player, ChatColor.RED + plugin.myLocale(player.getUniqueId()).setHomeerrorNotOnIsland);
            return;
        }

        plugin.getPlayers().setHomeLocation(player.getUniqueId(), player.getLocation(), number);
        if (number == 1) {
            Util.sendMessage(player, ChatColor.GREEN + plugin.myLocale(player.getUniqueId()).setHomehomeSet);
        } else {
            Util.sendMessage(player, ChatColor.GREEN + plugin.myLocale(player.getUniqueId()).setHomehomeSet + " #" + number);
        }
    }

    /**
     * Checks if an online player is in the protected area of their island, a team island or a
     * coop island
     *
     * @param coop - if true, coop islands are included
     * @return true if on valid island, false if not
     */
    public boolean playerIsOnIsland(final Player player, boolean coop) {
        return locationIsAtHome(player, coop, player.getLocation());
    }

    /**
     * Checks if a location is within the home boundaries of a player. If coop is true, this check includes coop players.
     *
     * @return true if the location is within home boundaries
     */
    public boolean locationIsAtHome(final Player player, boolean coop, Location loc) {
        // Make a list of test locations and test them
        Set<Location> islandTestLocations = new HashSet<>();
        if (plugin.getPlayers().hasIsland(player.getUniqueId())) {
            islandTestLocations.add(plugin.getPlayers().getIslandLocation(player.getUniqueId()));

            if (Settings.createNether && Settings.newNether && ASkyBlock.getNetherWorld() != null) {
                islandTestLocations.add(netherIsland(plugin.getPlayers().getIslandLocation(player.getUniqueId())));
            }
        } else if (plugin.getPlayers().inTeam(player.getUniqueId())) {
            islandTestLocations.add(plugin.getPlayers().getTeamIslandLocation(player.getUniqueId()));

            if (Settings.createNether && Settings.newNether && ASkyBlock.getNetherWorld() != null) {
                islandTestLocations.add(netherIsland(plugin.getPlayers().getTeamIslandLocation(player.getUniqueId())));
            }
        }

        if (coop) {
            islandTestLocations.addAll(CoopPlay.getInstance().getCoopIslands(player));
        }

        if (islandTestLocations.isEmpty()) {
            return false;
        }

        for (Location islandTestLocation : islandTestLocations) {
            if (islandTestLocation != null && islandTestLocation.getWorld() != null
                    && islandTestLocation.getWorld().equals(loc.getWorld())) {
                int protectionRange = Settings.islandProtectionRange;
                if (getIslandAt(islandTestLocation) != null) {
                    Island island = getProtectedIslandAt(islandTestLocation);
                    if (island != null) {
                        protectionRange = island.getProtectionSize();
                    }
                }

                if (loc.getX() > islandTestLocation.getX() - protectionRange / 2
                        && loc.getX() < islandTestLocation.getX() + protectionRange / 2
                        && loc.getZ() > islandTestLocation.getZ() - protectionRange / 2
                        && loc.getZ() < islandTestLocation.getZ() + protectionRange / 2) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the island being public at the location or null if there is none
     *
     * @return PlayerIsland object
     */
    public Island getProtectedIslandAt(Location location) {
        if (spawn != null && spawn.onIsland(location)) {
            return spawn;
        }

        Island island = getIslandAt(location);
        if (island == null) {
            return null;
        }

        if (island.onIsland(location)) {
            return island;
        }
        return null;
    }

    /**
     * Generates a Nether version of the locations
     */
    private Location netherIsland(Location islandLocation) {
        return islandLocation.toVector().toLocation(ASkyBlock.getNetherWorld());
    }

    /**
     * Checks if a specific location is within the protected range of an island
     * owned by the player
     *
     * @return true if location is on island of player
     */
    public boolean locationIsOnIsland(final Player player, final Location loc) {
        if (player == null) {
            return false;
        }

        Island island = getIslandAt(loc);
        if (island != null) {
            return island.onIsland(loc) && island.getMembers().contains(player.getUniqueId());
        }

        Set<Location> islandTestLocations = new HashSet<>();
        if (plugin.getPlayers().hasIsland(player.getUniqueId())) {
            islandTestLocations.add(plugin.getPlayers().getIslandLocation(player.getUniqueId()));
        } else if (plugin.getPlayers().inTeam(player.getUniqueId())) {
            islandTestLocations.add(plugin.getPlayers().get(player.getUniqueId()).getTeamIslandLocation());
        }
        // Check any coop locations
        islandTestLocations.addAll(CoopPlay.getInstance().getCoopIslands(player));
        if (islandTestLocations.isEmpty()) {
            return false;
        }
        // Run through all the locations
        for (Location islandTestLocation : islandTestLocations) {
            if (loc.getWorld().equals(islandTestLocation.getWorld())
                    && (loc.getX() >= islandTestLocation.getX() - Settings.islandProtectionRange / 2
                    && loc.getX() < islandTestLocation.getX() + Settings.islandProtectionRange / 2
                    && loc.getZ() >= islandTestLocation.getZ() - Settings.islandProtectionRange / 2
                    && loc.getZ() < islandTestLocation.getZ() + Settings.islandProtectionRange / 2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds out if location is within a set of island locations and returns the
     * one that is there or null if not
     *
     * @return Location found that is on the island
     */
    public Location locationIsOnIsland(final Set<Location> islandTestLocations, final Location loc) {
        // Run through all the locations
        for (Location islandTestLocation : islandTestLocations) {
            if (loc.getWorld().equals(islandTestLocation.getWorld())) {
                if (getIslandAt(islandTestLocation) != null) {
                    Island island = getProtectedIslandAt(islandTestLocation);
                    if (island != null) {
                        return island.getCenter();
                    }
                } else if (loc.getX() > islandTestLocation.getX() - Settings.islandProtectionRange / 2
                        && loc.getX() < islandTestLocation.getX() + Settings.islandProtectionRange / 2
                        && loc.getZ() > islandTestLocation.getZ() - Settings.islandProtectionRange / 2
                        && loc.getZ() < islandTestLocation.getZ() + Settings.islandProtectionRange / 2) {
                    return islandTestLocation;
                }
            }
        }
        return null;
    }

    /**
     * Checks if an online player is in the protected area of their island, a team island or a
     * coop island
     *
     * @return true if on valid island, false if not
     */
    public boolean playerIsOnIsland(final Player player) {
        return playerIsOnIsland(player, true);
    }

    /**
     * Checks to see if a player is trespassing on another player's island
     * Both players must be online.
     *
     * @param owner - owner or team member of an island
     * @return true if they are on the island otherwise false.
     */
    public boolean isOnIsland(final Player owner, final Player target) {
        // Check world
        if (target.getWorld().equals(ASkyBlock.getIslandWorld()) || (Settings.newNether && Settings.createNether && target.getWorld()
                .equals(ASkyBlock.getNetherWorld()))) {

            Location islandTestLocation;
            if (plugin.getPlayers().inTeam(owner.getUniqueId())) {
                if (plugin.getPlayers().getMembers(plugin.getPlayers().getTeamLeader(owner.getUniqueId())).contains(target.getUniqueId())) {
                    return false;
                }
                islandTestLocation = plugin.getPlayers().getTeamIslandLocation(owner.getUniqueId());
            } else {
                islandTestLocation = plugin.getPlayers().getIslandLocation(owner.getUniqueId());
            }

            if (islandTestLocation == null) {
                return false;
            }

            int protectionRange = Settings.islandProtectionRange;
            if (getIslandAt(islandTestLocation) != null) {

                Island island = getProtectedIslandAt(islandTestLocation);
                // Get the protection range for this location if possible
                if (island != null) {
                    // We are in a protected island area.
                    protectionRange = island.getProtectionSize();
                }
            }

            return target.getLocation().getX() > islandTestLocation.getX() - protectionRange / 2
                    && target.getLocation().getX() < islandTestLocation.getX() + protectionRange / 2
                    && target.getLocation().getZ() > islandTestLocation.getZ() - protectionRange / 2
                    && target.getLocation().getZ() < islandTestLocation.getZ() + protectionRange / 2;

        }
        return false;
    }

    /**
     * Transfers ownership of an island from one player to another
     *
     * @return true if successful
     */
    public boolean transferIsland(final UUID oldOwner, final UUID newOwner) {
        if (plugin.getPlayers().hasIsland(oldOwner)) {
            Location islandLoc = plugin.getPlayers().getIslandLocation(oldOwner);
            plugin.getPlayers().setHasIsland(newOwner, true);
            plugin.getPlayers().setIslandLocation(newOwner, islandLoc);
            plugin.getPlayers().setTeamIslandLocation(newOwner, null);
            plugin.getPlayers().setHasIsland(oldOwner, false);
            plugin.getPlayers().setIslandLocation(oldOwner, null);
            plugin.getPlayers().setTeamIslandLocation(oldOwner, islandLoc);
            Island island = getIslandAt(islandLoc);
            if (island != null) {
                setIslandOwner(island, newOwner);
            }

            TopTen.remove(oldOwner);
            Bukkit.getPluginManager().callEvent(new IslandChangeOwnerEvent(island, oldOwner, newOwner));
            return true;
        }
        return false;
    }

    /**
     * Sets an island to be owned by another player. If the new owner had an
     * island, that island is released to null ownership
     */
    public void setIslandOwner(Island island, UUID newOwner) {
        UUID oldOwner = island.getOwner();
        if (newOwner == null && oldOwner != null) {
            ownershipMap.remove(oldOwner);
            island.setOwner(null);
            return;
        }

        if (ownershipMap.containsKey(newOwner)) {
            Island oldIsland = ownershipMap.get(newOwner);
            oldIsland.setOwner(null);
            ownershipMap.remove(newOwner);
        }

        if (newOwner != null) {
            island.setOwner(newOwner);
            if (oldOwner != null && ownershipMap.containsKey(oldOwner)) {
                ownershipMap.remove(oldOwner);
            }
            ownershipMap.put(newOwner, island);
        }
    }

    /**
     * Removes monsters around location l
     */
    public void removeMobs(final Location l) {
        if (!inWorld(l)) {
            return;
        }

        if (isAtSpawn(l)) {
            return;
        }

        final int px = l.getBlockX(), py = l.getBlockY(), pz = l.getBlockZ();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                final Chunk c = l.getWorld().getChunkAt(new Location(l.getWorld(), px + x * 16, py, pz + z * 16));
                if (c.isLoaded()) {
                    for (final Entity e : c.getEntities()) {
                        if (e.getCustomName() != null || e.hasMetadata("NPC")) {
                            continue;
                        }
                        if (e instanceof Monster && !Settings.mobWhiteList.contains(e.getType())) {
                            e.remove();
                        }
                    }
                }
            }
        }
    }

    /**
     * Indicates whether a player is at the island spawn or not
     *
     * @return true if they are, false if they are not, or spawn does not exist
     */
    public boolean isAtSpawn(Location playerLoc) {
        return spawn != null && spawn.onIsland(playerLoc);
    }

    /**
     * This removes players from an island overworld and nether - used when reseting or deleting an island
     * Mobs are killed when the chunks are refreshed.
     *
     * @param island to remove players from
     */
    public void removePlayersFromIsland(final Island island, UUID uuid) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (island.inIslandSpace(player.getLocation())) {
                if (!player.getUniqueId().equals(uuid) && (plugin.getPlayers().hasIsland(player.getUniqueId())
                        || plugin.getPlayers().inTeam(player.getUniqueId()))) {
                    homeTeleport(player);
                } else {
                    Island spawn = getSpawn();
                    if (spawn != null) {
                        player.teleport(ASkyBlock.getIslandWorld().getSpawnLocation());
                    } else {
                        if (!player.performCommand(Settings.SPAWNCOMMAND)) {
                            plugin.getLogger().warning("During island deletion player " + player.getName()
                                    + " could not be sent to spawn so was dropped, sorry.");
                        }
                    }
                }
            }
        }
    }

    /**
     * This teleports player to their island. If not safe place can be found
     * then the player is sent to spawn via /spawn command
     *
     * @return true if the home teleport is successful
     */
    public boolean homeTeleport(final Player player) {
        return homeTeleport(player, 1);
    }

    /**
     * Teleport player to a home location. If one cannot be found a search is done to
     * find a safe place.
     *
     * @param number - home location to do to
     * @return true if successful, false if not
     */
    public boolean homeTeleport(final Player player, int number) {
        Location home;
        home = getSafeHomeLocation(player.getUniqueId(), number);
        if (player.isInsideVehicle()) {
            Entity boat = player.getVehicle();
            if (boat instanceof Boat) {
                player.leaveVehicle();
                // Remove the boat so they don't lie around everywhere
                boat.remove();
                player.getInventory().addItem(new ItemStack(Material.BOAT, 1));
                player.updateInventory();
            }
        }
        if (home == null) {
            new SafeSpotTeleport(plugin, player, plugin.getPlayers().getHomeLocation(player.getUniqueId(), number), number);
            return true;
        }

        player.teleport(home);
        if (number == 1) {
            Util.sendMessage(player, ChatColor.GREEN + plugin.myLocale(player.getUniqueId()).islandteleport);
        } else {
            Util.sendMessage(player, ChatColor.GREEN + plugin.myLocale(player.getUniqueId()).islandteleport + " #" + number);
        }
        return true;

    }

    /**
     * Determines a safe teleport spot on player's island or the team island
     * they belong to.
     *
     * @param p UUID of player
     * @param number - starting home location e.g., 1
     * @return Location of a safe teleport spot or null if one cannot be found
     */
    public Location getSafeHomeLocation(final UUID p, int number) {
        Location l = plugin.getPlayers().getHomeLocation(p, number);
        if (l == null) {
            number = 1;
            l = plugin.getPlayers().getHomeLocation(p, number);
        }

        if (l != null) {
            if (isSafeLocation(l)) {
                return l.clone().add(new Vector(0.5D, 0, 0.5D));
            }

            Location lPlusOne = l.clone();
            lPlusOne.add(new Vector(0, 1, 0));
            if (isSafeLocation(lPlusOne)) {
                // Adjust the home location accordingly
                plugin.getPlayers().setHomeLocation(p, lPlusOne, number);
                return lPlusOne.clone().add(new Vector(0.5D, 0, 0.5D));
            }
        }

        if (plugin.getPlayers().inTeam(p)) {
            l = plugin.getPlayers().getTeamIslandLocation(p);
            if (isSafeLocation(l)) {
                plugin.getPlayers().setHomeLocation(p, l, number);
                return l.clone().add(new Vector(0.5D, 0, 0.5D));
            } else {
                // try team leader's home
                Location tlh = plugin.getPlayers().getHomeLocation(plugin.getPlayers().getTeamLeader(p));
                if (tlh != null && (isSafeLocation(tlh))) {
                    plugin.getPlayers().setHomeLocation(p, tlh, number);
                    return tlh.clone().add(new Vector(0.5D, 0, 0.5D));
                }
            }
        } else {
            l = plugin.getPlayers().getIslandLocation(p);
            if (isSafeLocation(l)) {
                plugin.getPlayers().setHomeLocation(p, l, number);
                return l.clone().add(new Vector(0.5D, 0, 0.5D));
            }
        }

        if (l == null) {
            plugin.getLogger().warning(plugin.getPlayers().getName(p) + " player has no island!");
            return null;
        }

        Location dl = new Location(l.getWorld(), l.getX() + 0.5D, l.getY() + 5D, l.getZ() + 2.5D, 0F, 30F);
        if (isSafeLocation(dl)) {
            plugin.getPlayers().setHomeLocation(p, dl, number);
            return dl;
        }

        dl = new Location(l.getWorld(), l.getX() + 0.5D, l.getY() + 5D, l.getZ() + 0.5D, 0F, 30F);
        if (isSafeLocation(dl)) {
            plugin.getPlayers().setHomeLocation(p, dl, number);
            return dl;
        }

        for (int y = l.getBlockY(); y < 255; y++) {
            final Location n = new Location(l.getWorld(), l.getX() + 0.5D, y, l.getZ() + 0.5D);
            if (isSafeLocation(n)) {
                plugin.getPlayers().setHomeLocation(p, n, number);
                return n;
            }
        }
        return null;
    }

    /**
     * @return a list of unowned islands
     */
    public HashMap<String, Island> getUnownedIslands() {
        HashMap<String, Island> result = new HashMap<>();
        for (Entry<Integer, TreeMap<Integer, Island>> x : islandGrid.entrySet()) {
            for (Island island : x.getValue().values()) {
                //plugin.getLogger().info("DEBUG: checking island at " + island.getCenter());
                if (island.getOwner() == null && !island.isSpawn() && !island.isPurgeProtected()) {
                    Location center = island.getCenter();
                    String serialized = island.getCenter().getWorld().getName() + ":" + center.getBlockX() + ":" + center.getBlockY()
                            + ":" + center.getBlockZ();
                    result.put(serialized, island);
                }
            }
        }
        return result;
    }

    /**
     * @return the spawnPoint or null if spawn does not exist
     */
    public Location getSpawnPoint() {
        if (spawn == null) {
            return null;
        }
        return spawn.getSpawnPoint();
    }

    /**
     * Set the spawn point for the island world
     */
    public void setSpawnPoint(Location location) {
        ASkyBlock.getIslandWorld().setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        spawn.setSpawnPoint(location);
    }

    /**
     * @return how many islands are in the grid
     */
    public int getIslandCount() {
        return ownershipMap.size();
    }

    /**
     * Get the ownership map of islands
     *
     * @return Hashmap of owned islands with owner UUID as a key
     */
    public Map<UUID, Island> getOwnedIslands() {
        return ownershipMap;
    }

    /**
     * Get name of the island owned by owner
     *
     * @return Returns the name of owner's island, or the owner's name if there is none.
     */
    public String getIslandName(UUID owner) {
        if (owner == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', islandNames.getString(owner.toString(), plugin.getPlayers().getName(owner)))
                + ChatColor.RESET;
    }

    /**
     * Set the island name
     */
    public void setIslandName(UUID owner, String name) {
        islandNames.set(owner.toString(), name);
        try {
            islandNames.save(islandNameFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save islandnames.yml! " + e.getMessage());
        }
    }

}
