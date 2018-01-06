package com.wasteofplastic.askyblock;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.wasteofplastic.askyblock.util.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Stores all the info about an island
 * Managed by GridManager
 *
 * @author tastybento
 */
public class Island implements Cloneable {

    // Island protection settings
    private static List<String> islandSettingsKey = new ArrayList<>();

    static {
        islandSettingsKey.clear();
        islandSettingsKey.add("");
    }

    ASkyBlock plugin;
    private Biome biome;
    private int minX, minZ, y;
    private int minProtectedX, minProtectedZ;
    private int protectionRange;
    private Location center, spawnPoint;
    private World world;
    private UUID owner;
    private long createdDate, updatedDate;
    private String password;
    private int votes;
    private int islandDistance;
    private boolean locked = false, isSpawn = false, purgeProtected;
    private Multiset<Material> tileEntityCount = HashMultiset.create();
    private HashMap<SettingsFlag, Boolean> igs = new HashMap<>();
    private int levelHandicap;

    /**
     * New island by loading islands.yml
     */
    public Island(ASkyBlock plugin, String serial, List<String> settingsKey) {
        this.plugin = plugin;
        String[] split = serial.split(":");
        try {
            protectionRange = Integer.parseInt(split[3]);
            islandDistance = Integer.parseInt(split[4]);
            int x = Integer.parseInt(split[0]), z = Integer.parseInt(split[2]);

            minX = x - islandDistance / 2;
            y = Integer.parseInt(split[1]);
            minZ = z - islandDistance / 2;
            minProtectedX = x - protectionRange / 2;
            minProtectedZ = z - protectionRange / 2;
            world = ASkyBlock.getIslandWorld();
            center = new Location(world, x, y, z);
            createdDate = new Date().getTime();
            updatedDate = createdDate;
            password = "";
            votes = 0;
            // Get locked status
            locked = split.length > 6 && split[6].equalsIgnoreCase("true");

            // Check if deletable
            purgeProtected = split.length > 7 && split[7].equalsIgnoreCase("true");

            if (!split[5].equals("null") && split[5].equals("spawn")) {
                isSpawn = true;
                if (split.length > 8) {
                    spawnPoint = Util.getLocationString(serial.substring(serial.indexOf(":SP:") + 4));
                }
            } else {
                owner = UUID.fromString(split[5]);
            }

            // Check if protection options there
            setSettings((split.length > 8 ? split[8] : null), settingsKey);

            // Get the biome
            if (split.length > 9) {
                try {
                    biome = Biome.valueOf(split[9]);
                } catch (IllegalArgumentException ignored) {
                }
            }

            // Get island level handicap
            if (split.length > 10) {
                try {
                    levelHandicap = Integer.valueOf(split[10]);
                } catch (Exception e) {
                    levelHandicap = 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets the settings for the island.
     */
    public void setSettings(String settings, List<String> settingsKey) {
        if (isSpawn) {
            setSpawnDefaults();
        } else {
            setIgsDefaults();
        }

        if (settings == null || settings.isEmpty()) {
            return;
        }
        if (settingsKey.size() != settings.length()) {
            plugin.getLogger().severe("Island settings does not match settings key in islands.yml. Using defaults.");
            return;
        }

        for (int i = 0; i < settingsKey.size(); i++) {
            try {
                if (settings.charAt(i) == '0') {
                    setIgsFlag(SettingsFlag.valueOf(settingsKey.get(i)), false);
                } else {
                    setIgsFlag(SettingsFlag.valueOf(settingsKey.get(i)), true);
                }
            } catch (Exception e) {
                // do nothing - bad value, probably a downgrade
            }
        }
    }

    /**
     * Resets the protection settings to their default as set in config.yml for this island
     */
    public void setIgsDefaults() {
        for (SettingsFlag flag : SettingsFlag.values()) {
            if (!Settings.defaultIslandSettings.containsKey(flag) || Settings.defaultIslandSettings.get(flag) == null) {
                igs.put(flag, (flag == SettingsFlag.MOB_SPAWN || flag == SettingsFlag.MONSTER_SPAWN));
            } else {
                igs.put(flag, Settings.defaultIslandSettings.get(flag));
            }
        }
    }

    /**
     * Reset spawn protection settings to their default as set in config.yml for this island
     */
    public void setSpawnDefaults() {
        for (SettingsFlag flag : SettingsFlag.values()) {
            if (!Settings.defaultIslandSettings.containsKey(flag) || Settings.defaultIslandSettings.get(flag) == null) {
                igs.put(flag, (flag == SettingsFlag.MOB_SPAWN || flag == SettingsFlag.MONSTER_SPAWN));
            } else {
                igs.put(flag, Settings.defaultIslandSettings.get(flag));
            }
        }
    }

    /**
     * Set the Island Guard flag
     */
    public void setIgsFlag(SettingsFlag flag, boolean value) {
        this.igs.put(flag, value);
    }

    /**
     * Add a new island using the island center method
     */
    public Island(ASkyBlock plugin, int x, int z) {
        this(plugin, x, z, null);
    }

    public Island(ASkyBlock plugin, int x, int z, UUID owner) {
        this.plugin = plugin;
        this.minX = x - Settings.islandDistance / 2;
        this.minZ = z - Settings.islandDistance / 2;
        this.minProtectedX = x - Settings.islandProtectionRange / 2;
        this.minProtectedZ = z - Settings.islandProtectionRange / 2;
        this.y = Settings.islandHeight;
        this.islandDistance = Settings.islandDistance;
        this.protectionRange = Settings.islandProtectionRange;
        this.world = ASkyBlock.getIslandWorld();
        this.center = new Location(world, x, y, z);
        this.createdDate = new Date().getTime();
        this.updatedDate = createdDate;
        this.password = "";
        this.votes = 0;
        this.owner = owner;
        setIgsDefaults();
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            // This should never happen
            throw new InternalError(e.toString());
        }
    }

    /**
     * Checks if location is anywhere in the island space (island distance)
     *
     * @return true if in the area
     */
    public boolean inIslandSpace(Location target) {
        return (target.getWorld().equals(ASkyBlock.getIslandWorld()) || target.getWorld().equals(ASkyBlock.getNetherWorld()))
                && target.getX() >= center.getBlockX() - islandDistance / 2 && target.getX() < center.getBlockX() + islandDistance / 2
                && target.getZ() >= center.getBlockZ() - islandDistance / 2 && target.getZ() < center.getBlockZ() + islandDistance / 2;
    }

    public boolean inIslandSpace(int x, int z) {
        return x >= center.getBlockX() - islandDistance / 2 && x < center.getBlockX() + islandDistance / 2
                && z >= center.getBlockZ() - islandDistance / 2 && z < center.getBlockZ() + islandDistance / 2;
    }

    /**
     * @return the minX
     */
    public int getMinX() {
        return minX;
    }

    /**
     * @param minX the minX to set
     */
    public void setMinX(int minX) {
        this.minX = minX;
    }

    /**
     * @return the z
     */
    public int getMinZ() {
        return minZ;
    }

    /**
     * @param minZ the z to set
     */
    public void setMinZ(int minZ) {
        this.minZ = minZ;
    }

    /**
     * @return the islandDistance
     */
    public int getIslandDistance() {
        return islandDistance;
    }

    /**
     * @param islandDistance the islandDistance to set
     */
    public void setIslandDistance(int islandDistance) {
        this.islandDistance = islandDistance;
    }

    /**
     * @return the center
     */
    public Location getCenter() {
        return center;
    }

    /**
     * @param center the center to set
     */
    public void setCenter(Location center) {
        this.center = center;
    }

    /**
     * @return the owner
     */
    public UUID getOwner() {
        return owner;
    }

    /**
     * @param owner the owner to set
     */
    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    /**
     * @return the createdDate
     */
    public long getCreatedDate() {
        return createdDate;
    }

    /**
     * @param createdDate the createdDate to set
     */
    public void setCreatedDate(long createdDate) {
        this.createdDate = createdDate;
    }

    /**
     * @return the updatedDate
     */
    public long getUpdatedDate() {
        return updatedDate;
    }

    /**
     * @param updatedDate the updatedDate to set
     */
    public void setUpdatedDate(long updatedDate) {
        this.updatedDate = updatedDate;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return the votes
     */
    public int getVotes() {
        return votes;
    }

    /**
     * @param votes the votes to set
     */
    public void setVotes(int votes) {
        this.votes = votes;
    }

    /**
     * @return the locked
     */
    public boolean isLocked() {
        return locked;
    }

    /**
     * @param locked the locked to set
     */
    public void setLocked(boolean locked) {
        // Bukkit.getLogger().info("DEBUG: island is now " + locked);
        this.locked = locked;
    }

    /**
     * Serializes the island for island.yml storage
     *
     * @return string that represents the island settings
     */
    public String save() {
        // x:height:z:protection range:island distance:owner UUID
        String ownerString = "null";
        if (isSpawn) {
            ownerString = "spawn";
            if (spawnPoint != null) {
                return center.getBlockX() + ":" + center.getBlockY() + ":" + center.getBlockZ() + ":" + protectionRange + ":"
                        + islandDistance + ":" + ownerString + ":" + locked + ":" + purgeProtected + ":SP:" + Util.getStringLocation(spawnPoint);
            }
            return center.getBlockX() + ":" + center.getBlockY() + ":" + center.getBlockZ() + ":" + protectionRange + ":"
                    + islandDistance + ":" + ownerString + ":" + locked + ":" + purgeProtected;
        }
        // Not spawn
        if (owner != null) {
            ownerString = owner.toString();
        }

        return center.getBlockX() + ":" + center.getBlockY() + ":" + center.getBlockZ() + ":" + protectionRange + ":"
                + islandDistance + ":" + ownerString + ":" + locked + ":" + purgeProtected + ":" + getSettings() + ":"
                + getBiome().toString() + ":" + levelHandicap;
    }

    /**
     * @return Serialized set of settings
     */
    public String getSettings() {
        StringBuilder result = new StringBuilder();
        try {
            for (SettingsFlag f : SettingsFlag.values()) {
                if (igs.containsKey(f)) {
                    result.append(this.igs.get(f) ? "1" : "0");
                } else {
                    result.append("0");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = new StringBuilder();
        }
        return result.toString();
    }

    /**
     * @return the biome
     */
    public Biome getBiome() {
        if (biome == null) {
            biome = center.getBlock().getBiome();
        }
        return biome;
    }

    /**
     * @param biome the biome to set
     */
    public void setBiome(Biome biome) {
        this.biome = biome;
    }

    /**
     * Get the Island Guard flag status
     *
     * @return true or false, or false if flag is not in the list
     */
    public boolean getIgsFlag(SettingsFlag flag) {
        if (this.igs.containsKey(flag)) {
            return igs.get(flag);
        }
        return false;
    }

    /**
     * Provides a list of all the players who are allowed on this island
     * including coop members
     *
     * @return a list of UUIDs that have legitimate access to the island
     */
    public List<UUID> getMembers() {
        // Add any coop members for this island
        List<UUID> result = new ArrayList<>(CoopPlay.getInstance().getCoopPlayers(center.toVector().toLocation(ASkyBlock.getIslandWorld())));
        if (Settings.createNether && Settings.newNether && ASkyBlock.getNetherWorld() != null) {
            result.addAll(CoopPlay.getInstance().getCoopPlayers(center.toVector().toLocation(ASkyBlock.getNetherWorld())));
        }
        if (owner == null) {
            return result;
        }
        result.add(owner);
        // Add any team members
        result.addAll(plugin.getPlayers().getMembers(owner));
        return result;
    }

    /**
     * @return the isSpawn
     */
    public boolean isSpawn() {
        return isSpawn;
    }

    /**
     * @param isSpawn the isSpawn to set
     */
    public void setSpawn(boolean isSpawn) {
        this.isSpawn = isSpawn;
    }

    /**
     * @return the islandDeletable
     */
    public boolean isPurgeProtected() {
        return purgeProtected;
    }

    /**
     * @param purgeProtected the islandDeletable to set
     */
    public void setPurgeProtected(boolean purgeProtected) {
        this.purgeProtected = purgeProtected;
    }

    /**
     * @return Provides count of villagers within the protected island boundaries
     */
    public int getPopulation() {
        int result = 0;
        for (int x = getMinProtectedX() / 16; x <= (getMinProtectedX() + getProtectionSize() - 1) / 16; x++) {
            for (int z = getMinProtectedZ() / 16; z <= (getMinProtectedZ() + getProtectionSize() - 1) / 16; z++) {
                for (Entity entity : world.getChunkAt(x, z).getEntities()) {
                    if (entity instanceof Villager && onIsland(entity.getLocation())) {
                        result++;
                    }
                }
            }
        }
        return result;
    }

    /**
     * Checks if a location is within this island's protected area
     *
     * @return true if it is, false if not
     */
    public boolean onIsland(Location target) {
        return (target.getWorld().equals(world) || (Settings.createNether && Settings.newNether && ASkyBlock.getNetherWorld() != null
                && target.getWorld().equals(ASkyBlock.getNetherWorld()))) && target.getBlockX() >= minProtectedX
                && target.getBlockX() < (minProtectedX + protectionRange) && target.getBlockZ() >= minProtectedZ
                && target.getBlockZ() < (minProtectedZ + protectionRange);
    }

    /**
     * @return the minprotectedX
     */
    public int getMinProtectedX() {
        return minProtectedX;
    }

    /**
     * @return the minProtectedZ
     */
    public int getMinProtectedZ() {
        return minProtectedZ;
    }

    /**
     * @return the protectionRange
     */
    public int getProtectionSize() {
        return protectionRange;
    }

    /**
     * @param protectionSize the protectionSize to set
     */
    public void setProtectionSize(int protectionSize) {
        this.protectionRange = protectionSize;
        this.minProtectedX = center.getBlockX() - protectionSize / 2;
        this.minProtectedZ = center.getBlockZ() - protectionSize / 2;

    }

    /**
     * @return number of hoppers on the island
     */
    public int getHopperCount() {
        tileEntityCount.clear();
        int result = 0;
        for (int x = getMinProtectedX() / 16; x <= (getMinProtectedX() + getProtectionSize() - 1) / 16; x++) {
            for (int z = getMinProtectedZ() / 16; z <= (getMinProtectedZ() + getProtectionSize() - 1) / 16; z++) {
                for (BlockState holder : world.getChunkAt(x, z).getTileEntities()) {
                    if (holder instanceof Hopper && onIsland(holder.getLocation())) {
                        result++;
                    }
                }
            }
        }
        return result;
    }

    /**
     * @return count of how many tile entities of type mat are on the island at last count. Counts are done when a player places
     * a tile entity.
     */
    public int getTileEntityCount(Material material, World world) {
        int result = 0;
        for (int x = getMinProtectedX() / 16; x <= (getMinProtectedX() + getProtectionSize() - 1) / 16; x++) {
            for (int z = getMinProtectedZ() / 16; z <= (getMinProtectedZ() + getProtectionSize() - 1) / 16; z++) {
                for (BlockState holder : world.getChunkAt(x, z).getTileEntities()) {
                    //plugin.getLogger().info("DEBUG: tile entity: " + holder.getType());
                    if (onIsland(holder.getLocation())) {
                        if (holder.getType() == material) {
                            result++;
                        } else if (material.equals(Material.REDSTONE_COMPARATOR_OFF)) {
                            if (holder.getType().equals(Material.REDSTONE_COMPARATOR_ON)) {
                                result++;
                            }
                        } else if (material.equals(Material.FURNACE)) {
                            if (holder.getType().equals(Material.BURNING_FURNACE)) {
                                result++;
                            }
                        } else if (material.toString().endsWith("BANNER")) {
                            if (holder.getType().toString().endsWith("BANNER")) {
                                result++;
                            }
                        } else if (material.equals(Material.WALL_SIGN) || material.equals(Material.SIGN_POST)) {
                            if (holder.getType().equals(Material.WALL_SIGN) || holder.getType().equals(Material.SIGN_POST)) {
                                result++;
                            }
                        }
                    }
                }
                for (Entity holder : world.getChunkAt(x, z).getEntities()) {
                    //plugin.getLogger().info("DEBUG: entity: " + holder.getType());
                    if (holder.getType().toString().equals(material.toString()) && onIsland(holder.getLocation())) {
                        result++;
                    }
                }
            }
        }
        return result;
    }

    public Location getSpawnPoint() {
        return spawnPoint;
    }

    public void setSpawnPoint(Location location) {
        spawnPoint = location;

    }

    /**
     * Toggles the Island Guard Flag
     */
    public void toggleIgs(SettingsFlag flag) {
        if (igs.containsKey(flag)) {
            igs.put(flag, !igs.get(flag));
        }

    }

    /**
     * @return the levelHandicap
     */
    public int getLevelHandicap() {
        return levelHandicap;
    }

    /**
     * @param levelHandicap the levelHandicap to set
     */
    public void setLevelHandicap(int levelHandicap) {
        this.levelHandicap = levelHandicap;
    }

    /**
     * Island Guard Setting flags
     * Covers island, spawn and system settings
     */
    public enum SettingsFlag {
        /**
         * Water is acid above sea level
         */
        ACID_DAMAGE,
        /**
         * Anvil use
         */
        ANVIL,
        /**
         * Armor stand use
         */
        ARMOR_STAND,
        /**
         * Beacon use
         */
        BEACON,
        /**
         * Bed use
         */
        BED,
        /**
         * Can break blocks
         */
        BREAK_BLOCKS,
        /**
         * Can breed animals
         */
        BREEDING,
        /**
         * Can use brewing stand
         */
        BREWING,
        /**
         * Can empty or fill buckets
         */
        BUCKET,
        /**
         * Can collect lava
         */
        COLLECT_LAVA,
        /**
         * Can collect water
         */
        COLLECT_WATER,
        /**
         * Can open chests or hoppers or dispensers
         */
        CHEST,
        /**
         * Can eat and teleport with chorus fruit
         */
        CHORUS_FRUIT,
        /**
         * Can use the work bench
         */
        CRAFTING,
        /**
         * Allow creepers to hurt players (but not damage blocks)
         */
        CREEPER_PAIN,
        /**
         * Can trample crops
         */
        CROP_TRAMPLE,
        /**
         * Can open doors or trapdoors
         */
        DOOR,
        /**
         * Chicken eggs can be thrown
         */
        EGGS,
        /**
         * Can use the enchanting table
         */
        ENCHANTING,
        /**
         * Can throw ender pearls
         */
        ENDER_PEARL,
        /**
         * Can toggle enter/exit names to island
         */
        ENTER_EXIT_MESSAGES,
        /**
         * Fire use/placement in general
         */
        FIRE,
        /**
         * Can extinguish fires by punching them
         */
        FIRE_EXTINGUISH,
        /**
         * Allow fire spread
         */
        FIRE_SPREAD,
        /**
         * Can use furnaces
         */
        FURNACE,
        /**
         * Can use gates
         */
        GATE,
        /**
         * Can open horse or other animal inventories, e.g. llama
         */
        HORSE_INVENTORY,
        /**
         * Can ride an animal
         */
        HORSE_RIDING,
        /**
         * Can hurt friendly mobs, e.g. cows
         */
        HURT_MOBS,
        /**
         * Can hurt monsters
         */
        HURT_MONSTERS,
        /**
         * Can leash or unleash animals
         */
        LEASH,
        /**
         * Can use buttons or levers
         */
        LEVER_BUTTON,
        /**
         * Animals, etc. can spawn
         */
        MILKING,
        /**
         * Can do PVP in the nether
         */
        MOB_SPAWN,
        /**
         * Monsters can spawn
         */
        MONSTER_SPAWN,
        /**
         * Can operate jukeboxes, note boxes etc.
         */
        MUSIC,
        /**
         * Can place blocks
         */
        NETHER_PVP,
        /**
         * Can interact with redstone items, like diodes
         */
        PLACE_BLOCKS,
        /**
         * Can go through portals
         */
        PORTAL,
        /**
         * Will activate pressure plates
         */
        PRESSURE_PLATE,
        /**
         * Can do PVP in the overworld
         */
        PVP,
        /**
         * Cows can be milked
         */
        REDSTONE,
        /**
         * Spawn eggs can be used
         */
        SPAWN_EGGS,
        /**
         * Can shear sheep
         */
        SHEARING,
        /**
         * Can trade with villagers
         */
        VILLAGER_TRADING,
        /**
         * Visitors can drop items
         */
        VISITOR_ITEM_DROP,
        /**
         * Visitors can pick up items
         */
        VISITOR_ITEM_PICKUP
    }

}
