package com.wasteofplastic.askyblock;

import com.wasteofplastic.askyblock.NotSetup.Reason;
import com.wasteofplastic.askyblock.commands.AdminCmd;
import com.wasteofplastic.askyblock.commands.Challenges;
import com.wasteofplastic.askyblock.commands.IslandCmd;
import com.wasteofplastic.askyblock.events.IslandDeleteEvent;
import com.wasteofplastic.askyblock.events.ReadyEvent;
import com.wasteofplastic.askyblock.generators.ChunkGeneratorWorld;
import com.wasteofplastic.askyblock.listeners.AcidEffect;
import com.wasteofplastic.askyblock.listeners.ChatListener;
import com.wasteofplastic.askyblock.listeners.CleanSuperFlat;
import com.wasteofplastic.askyblock.listeners.EntityLimits;
import com.wasteofplastic.askyblock.listeners.FlyingMobEvents;
import com.wasteofplastic.askyblock.listeners.IslandGuard;
import com.wasteofplastic.askyblock.listeners.IslandGuard1_9;
import com.wasteofplastic.askyblock.listeners.JoinLeaveEvents;
import com.wasteofplastic.askyblock.listeners.LavaCheck;
import com.wasteofplastic.askyblock.listeners.NetherPortals;
import com.wasteofplastic.askyblock.listeners.NetherSpawning;
import com.wasteofplastic.askyblock.listeners.PlayerEvents;
import com.wasteofplastic.askyblock.listeners.PlayerEvents2;
import com.wasteofplastic.askyblock.listeners.PlayerEvents3;
import com.wasteofplastic.askyblock.listeners.WorldEnter;
import com.wasteofplastic.askyblock.panels.BiomesPanel;
import com.wasteofplastic.askyblock.panels.ControlPanel;
import com.wasteofplastic.askyblock.panels.SchematicsPanel;
import com.wasteofplastic.askyblock.panels.SettingsPanel;
import com.wasteofplastic.askyblock.panels.WarpPanel;
import com.wasteofplastic.askyblock.util.VaultHelper;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author tastybento
 * Main ASkyBlock class - provides an island minigame in a sea of acid
 */
public final class ASkyBlock extends JavaPlugin {

    private static final boolean DEBUG = false;
    private static ASkyBlock plugin;
    private static World islandWorld = null, netherWorld = null;
    private AcidTask acidTask;
    private boolean newIsland = false;
    private File playersFolder;
    private Challenges challenges;
    private PlayerCache players;
    private WarpSigns warpSignsListener;
    private LavaCheck lavaListener;
    private BiomesPanel biomes;
    private GridManager grid;
    private IslandCmd islandCmd;
    private TinyDB tinyDB;
    private WarpPanel warpPanel;
    private TopTen topTen;
    private Messages messages;
    private ChatListener chatListener;
    private SchematicsPanel schematicsPanel;
    private SettingsPanel settingsPanel;
    private PlayerEvents playerEvents;

    // Localization Strings
    private Map<String, ASLocale> availableLocales = new HashMap<>();

    /**
     * @return ASkyBlock object instance
     */
    public static ASkyBlock getPlugin() {
        return plugin;
    }

    /**
     * Returns the World object for the island world named in config.yml.
     * If the world does not exist then it is created.
     *
     * @return islandWorld - Bukkit World object for the ASkyBlock world
     */
    public static World getIslandWorld() {
        if (islandWorld == null) {
            //Bukkit.getLogger().info("DEBUG worldName = " + Settings.worldName);
            //
            if (Settings.useOwnGenerator) {
                islandWorld = Bukkit.getServer().getWorld(Settings.worldName);
                //Bukkit.getLogger().info("DEBUG world is " + islandWorld);
            } else {
                islandWorld = WorldCreator.name(Settings.worldName).type(WorldType.FLAT).environment(World.Environment.NORMAL)
                        .generator(new ChunkGeneratorWorld())
                        .createWorld();
            }
            // Make the nether if it does not exist
            if (Settings.createNether) {
                getNetherWorld();
            }
            // Multiverse configuration

            if (!Settings.useOwnGenerator && Bukkit.getServer().getPluginManager().isPluginEnabled("Multiverse-Core")) {
                Bukkit.getLogger().info("Trying to register generator with Multiverse ");
                try {
                    Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                            "mv import " + Settings.worldName + " normal -g " + plugin.getName());
                    if (!Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                            "mv modify set generator " + plugin.getName() + " " + Settings.worldName)) {
                        Bukkit.getLogger().severe("Multiverse is out of date! - Upgrade to latest version!");
                    }
                    if (Settings.createNether) {
                        if (Settings.newNether) {
                            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                                    "mv import " + Settings.worldName + "_nether nether -g " + plugin.getName());
                            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(),
                                    "mv modify set generator " + plugin.getName() + " " + Settings.worldName + "_nether");
                        } else {
                            Bukkit.getServer()
                                    .dispatchCommand(Bukkit.getServer().getConsoleSender(),
                                            "mv import " + Settings.worldName + "_nether nether");
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().severe("Not successfull! Disabling " + plugin.getName() + "!");
                    e.printStackTrace();
                    Bukkit.getServer().getPluginManager().disablePlugin(plugin);
                }
            }

        }
        // Set world settings
        if (islandWorld != null) {
            islandWorld.setWaterAnimalSpawnLimit(Settings.waterAnimalSpawnLimit);
            islandWorld.setMonsterSpawnLimit(Settings.monsterSpawnLimit);
            islandWorld.setAnimalSpawnLimit(Settings.animalSpawnLimit);
        }

        return islandWorld;
    }

    /**
     * @return the netherWorld
     */
    public static World getNetherWorld() {
        if (netherWorld == null && Settings.createNether) {
            if (Settings.useOwnGenerator) {
                return Bukkit.getServer().getWorld(Settings.worldName + "_nether");
            }
            if (Bukkit.getWorld(Settings.worldName + "_nether") == null) {
                Bukkit.getLogger().info("Creating " + plugin.getName() + "'s Nether...");
            }
            if (!Settings.newNether) {
                netherWorld =
                        WorldCreator.name(Settings.worldName + "_nether")
                                .type(WorldType.NORMAL)
                                .environment(World.Environment.NETHER)
                                .createWorld();
            } else {
                netherWorld = WorldCreator.name(Settings.worldName + "_nether")
                        .type(WorldType.FLAT)
                        .generator(new ChunkGeneratorWorld())
                        .environment(World.Environment.NETHER)
                        .createWorld();
            }
            netherWorld.setMonsterSpawnLimit(Settings.monsterSpawnLimit);
            netherWorld.setAnimalSpawnLimit(Settings.animalSpawnLimit);
        }
        return netherWorld;
    }

    @Override
    public void onDisable() {
        try {
            if (players != null) {
                players.removeAllPlayers();
            }
            if (grid != null) {
                // Save grid synchronously
                grid.saveGrid(false);
            }
            // Save the warps and do not reload the panel
            if (warpSignsListener != null) {
                warpSignsListener.saveWarpList();
            }
            if (messages != null) {
                messages.saveMessages();
            }
            TopTen.topTenSave();
            // Close the name database
            if (tinyDB != null) {
                tinyDB.saveDB();
            }
            // Save the coops
            CoopPlay.getInstance().saveCoops();
            // Remove temporary perms
            if (playerEvents != null) {
                playerEvents.removeAllTempPerms();
            }
        } catch (final Exception e) {
            getLogger().severe("Something went wrong saving files!");
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        plugin = this;
        new ASkyBlockAPI(this);

        saveDefaultConfig();
        // Check to see if island distance is set or not
        if (getConfig().getInt("island.distance", -1) < 1) {
            getLogger().severe("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+");
            getLogger().severe("More set up is required. Go to config.yml and edit it.");
            getLogger().severe("");
            getLogger().severe("Make sure you set island distance. If upgrading, set it to what it was before.");
            getLogger().severe("");
            getLogger().severe("+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+");
            if (Settings.GAMETYPE.equals(Settings.GameType.ASKYBLOCK)) {
                getCommand("island").setExecutor(new NotSetup(Reason.DISTANCE));
                getCommand("asc").setExecutor(new NotSetup(Reason.DISTANCE));
                getCommand("asadmin").setExecutor(new NotSetup(Reason.DISTANCE));
            } else {
                getCommand("ai").setExecutor(new NotSetup(Reason.DISTANCE));
                getCommand("aic").setExecutor(new NotSetup(Reason.DISTANCE));
                getCommand("acid").setExecutor(new NotSetup(Reason.DISTANCE));
            }
            return;
        }
        // Load all the configuration of the plugin and localization strings
        if (!PluginConfig.loadPluginConfig(this)) {
            // Currently, the only setup error is where the world_name does not match
            if (Settings.GAMETYPE.equals(Settings.GameType.ASKYBLOCK)) {
                getCommand("island").setExecutor(new NotSetup(Reason.WORLD_NAME));
                getCommand("asc").setExecutor(new NotSetup(Reason.WORLD_NAME));
                getCommand("asadmin").setExecutor(new NotSetup(Reason.WORLD_NAME));
            } else {
                getCommand("ai").setExecutor(new NotSetup(Reason.WORLD_NAME));
                getCommand("aic").setExecutor(new NotSetup(Reason.WORLD_NAME));
                getCommand("acid").setExecutor(new NotSetup(Reason.WORLD_NAME));
            }
            return;
        }
        if (Settings.useEconomy && !VaultHelper.setupEconomy()) {
            getLogger().warning("Could not set up economy! - Running without an economy.");
            Settings.useEconomy = false;
        }
        if (!VaultHelper.setupPermissions()) {
            getLogger().severe("Cannot link with Vault for permissions! Disabling plugin!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Get challenges
        challenges = new Challenges(this);
        // Set and make the player's directory if it does not exist and then
        // load players into memory
        playersFolder = new File(getDataFolder(), "players");
        if (!playersFolder.exists()) {
            playersFolder.mkdir();
        }
        if (DEBUG) {
            Bukkit.getLogger().info("DEBUG: Setting up player cache");
        }
        players = new PlayerCache(this);
        // Set up commands for this plugin
        islandCmd = new IslandCmd(this);
        if (Settings.GAMETYPE.equals(Settings.GameType.ASKYBLOCK)) {
            AdminCmd adminCmd = new AdminCmd(this);

            getCommand("island").setExecutor(islandCmd);
            getCommand("island").setTabCompleter(islandCmd);

            getCommand("asc").setExecutor(getChallenges());
            getCommand("asc").setTabCompleter(getChallenges());

            getCommand("asadmin").setExecutor(adminCmd);
            getCommand("asadmin").setTabCompleter(adminCmd);
        } else {
            AdminCmd adminCmd = new AdminCmd(this);

            getCommand("ai").setExecutor(islandCmd);
            getCommand("ai").setTabCompleter(islandCmd);

            getCommand("aic").setExecutor(getChallenges());
            getCommand("aic").setTabCompleter(getChallenges());

            getCommand("acid").setExecutor(adminCmd);
            getCommand("acid").setTabCompleter(adminCmd);
        }
        // Register events that this plugin uses
        // registerEvents();
        // Load messages
        messages = new Messages(this);
        messages.loadMessages();

        // Kick off a few tasks on the next tick
        // By calling getIslandWorld(), if there is no island
        // world, it will be created
        getServer().getScheduler().runTask(this, () -> {
            // Create the world if it does not exist. This is run after the
            // server starts.
            getIslandWorld();
            if (!Settings.useOwnGenerator && getServer().getWorld(Settings.worldName).getGenerator() == null) {
                // Check if the world generator is registered correctly
                getLogger().severe("********* The Generator for " + ASkyBlock.this.getName()
                        + " is not registered so the plugin cannot start ********");
                getLogger().severe(
                        "If you are using your own generator or server.properties, set useowngenerator: true in config.yml");
                getLogger().severe("Otherwise:");
                getLogger().severe("Make sure you have the following in bukkit.yml (case sensitive):");
                getLogger().severe("worlds:");
                getLogger().severe("  # The next line must be the name of your world:");
                getLogger().severe("  " + Settings.worldName + ":");
                getLogger().severe("    generator: " + ASkyBlock.this.getName());
                if (Settings.GAMETYPE.equals(Settings.GameType.ASKYBLOCK)) {
                    getCommand("island").setExecutor(new NotSetup(Reason.GENERATOR));
                    getCommand("asc").setExecutor(new NotSetup(Reason.GENERATOR));
                    getCommand("asadmin").setExecutor(new NotSetup(Reason.GENERATOR));
                } else {
                    getCommand("ai").setExecutor(new NotSetup(Reason.GENERATOR));
                    getCommand("aic").setExecutor(new NotSetup(Reason.GENERATOR));
                    getCommand("acid").setExecutor(new NotSetup(Reason.GENERATOR));
                }
                HandlerList.unregisterAll(this);
                return;
            }

            // Run game rule to keep things quiet
            if (Settings.silenceCommandFeedback) {
                getLogger().info("Silencing command feedback for Ops...");
                getServer().dispatchCommand(getServer().getConsoleSender(), "minecraft:gamerule sendCommandFeedback false");
                getLogger().info("If you do not want this, do /gamerule sendCommandFeedback true");
            }

            // Run these one tick later to ensure worlds are loaded.
            getServer().getScheduler().runTask(this, () -> {
                if (grid == null) {
                    grid = new GridManager(this);
                }

                registerEvents();

                if (tinyDB == null) {
                    tinyDB = new TinyDB(this);
                }

                getWarpSignsListener().loadWarpList();
                if (Settings.useWarpPanel) {
                    warpPanel = new WarpPanel(ASkyBlock.this);
                    getServer().getPluginManager().registerEvents(warpPanel, this);
                }
                // Load the TopTen GUI
                if (!Settings.displayIslandTopTenInChat) {
                    topTen = new TopTen(ASkyBlock.this);
                    getServer().getPluginManager().registerEvents(topTen, this);
                }

                getServer().getPluginManager().registerEvents(new ControlPanel(this), this);
                // Settings
                settingsPanel = new SettingsPanel(ASkyBlock.this);
                getServer().getPluginManager().registerEvents(settingsPanel, ASkyBlock.this);
                // Biomes
                // Load Biomes
                biomes = new BiomesPanel(ASkyBlock.this);
                getServer().getPluginManager().registerEvents(biomes, ASkyBlock.this);

                TopTen.topTenLoad();

                // Add any online players to the DB
                for (Player onlinePlayer : ASkyBlock.this.getServer().getOnlinePlayers()) {
                    tinyDB.savePlayerName(onlinePlayer.getName(), onlinePlayer.getUniqueId());
                }
                if (Settings.backupDuration > 0) {
                    new AsyncBackup(ASkyBlock.this);
                }
                // Load the coops
                if (Settings.persistantCoops) {
                    CoopPlay.getInstance().loadCoops();
                }
                // Give temp permissions
                playerEvents.giveAllTempPerms();

                getLogger().info("All files loaded. Ready to play...");

                // Fire event
                getServer().getPluginManager().callEvent(new ReadyEvent());
            });
            // Run acid tasks
            acidTask = new AcidTask(ASkyBlock.this);

        });
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(final String worldName, final String id) {
        return new ChunkGeneratorWorld();
    }

    /**
     * @return the challenges
     */
    public Challenges getChallenges() {
        /*
	if (challenges == null) {
	    challenges = new Challenges(this);
	}*/
        return challenges;
    }

    /**
     * Registers events
     */
    public void registerEvents() {
        final PluginManager manager = getServer().getPluginManager();
        // Nether portal events
        manager.registerEvents(new NetherPortals(this), this);
        // Nether spawning events
        manager.registerEvents(new NetherSpawning(this), this);
        // Island Protection events
        manager.registerEvents(new IslandGuard(this), this);
        // Island Entity Limits
        manager.registerEvents(new EntityLimits(this), this);
        // Player events
        playerEvents = new PlayerEvents(this);
        manager.registerEvents(playerEvents, this);
        try {
            Class<?> clazz = Class.forName("org.bukkit.event.entity.EntityPickupItemEvent", false, getClassLoader());
            if (clazz != null) {
                manager.registerEvents(new PlayerEvents3(this), this);
            }
        } catch (ClassNotFoundException e) {
            manager.registerEvents(new PlayerEvents2(this), this);
        }

        // Check for 1.9 material
        for (Material m : Material.values()) {
            if (m.name().equalsIgnoreCase("END_CRYSTAL")) {
                manager.registerEvents(new IslandGuard1_9(this), this);
                break;
            }
        }
        // Events for when a player joins or leaves the server
        manager.registerEvents(new JoinLeaveEvents(this), this);
        // Ensures Lava flows correctly in ASkyBlock world
        lavaListener = new LavaCheck(this);
        manager.registerEvents(lavaListener, this);
        // Ensures that water is acid
        manager.registerEvents(new AcidEffect(this), this);
        // Ensures that boats are safe in ASkyBlock
        if (Settings.acidDamage > 0D) {
            manager.registerEvents(new SafeBoat(this), this);
        }
        // Enables warp signs in ASkyBlock
        warpSignsListener = new WarpSigns(this);
        manager.registerEvents(warpSignsListener, this);
        // Control panel - for future use
        // manager.registerEvents(new ControlPanel(), this);
        // Change names of inventory items
        //manager.registerEvents(new AcidInventory(this), this);
        // Schematics panel
        schematicsPanel = new SchematicsPanel(this);
        manager.registerEvents(schematicsPanel, this);
        // Track incoming world teleports
        manager.registerEvents(new WorldEnter(this), this);
        // Team chat
        chatListener = new ChatListener(this);
        manager.registerEvents(chatListener, this);
        // Wither
        if (Settings.restrictWither) {
            manager.registerEvents(new FlyingMobEvents(this), this);
        }
        if (Settings.recoverSuperFlat) {
            manager.registerEvents(new CleanSuperFlat(), this);
        }
    }

    /**
     * @return the warpSignsListener
     */
    public WarpSigns getWarpSignsListener() {
        return warpSignsListener;
    }

    /**
     * @return the grid
     */
    public GridManager getGrid() {
        /*
	if (grid == null) {
	    grid = new GridManager(this);
	}*/
        return grid;
    }

    /**
     * Delete Island
     * Called when an island is restarted or reset
     *
     * @param player - player name String
     * @param removeBlocks - true to remove the island blocks
     */
    public void deletePlayerIsland(final UUID player, boolean removeBlocks) {
        // Removes the island
        //getLogger().info("DEBUG: deleting player island");
        CoopPlay.getInstance().clearAllIslandCoops(player);
        getWarpSignsListener().removeWarp(player);
        Island island = grid.getIsland(player);
        if (island != null) {
            if (removeBlocks) {
                grid.removePlayersFromIsland(island, player);
                new DeleteIslandChunk(this, island);
                //new DeleteIslandByBlock(this, island);
            } else {
                island.setLocked(false);
                grid.setIslandOwner(island, null);
            }
            getServer().getPluginManager().callEvent(new IslandDeleteEvent(player, island.getCenter()));
        } else {
            getLogger().severe("Could not delete player: " + player.toString() + " island!");
            getServer().getPluginManager().callEvent(new IslandDeleteEvent(player, null));
        }
        players.zeroPlayerData(player);
    }

    /**
     * @return the biomes
     */
    public BiomesPanel getBiomes() {
        return biomes;
    }

    /**
     * @return the players
     */
    public PlayerCache getPlayers() {
        /*
	if (players == null) {
	    players = new PlayerCache(this);
	}*/
        return players;
    }

    /**
     * @return the playersFolder
     */
    public File getPlayersFolder() {
        return playersFolder;
    }

    /**
     * @return the newIsland
     */
    public boolean isNewIsland() {
        return newIsland;
    }

    /**
     * @param newIsland the newIsland to set
     */
    public void setNewIsland(boolean newIsland) {
        this.newIsland = newIsland;
    }

    /**
     * Resets a player's inventory, armor slots, equipment, enderchest and
     * potion effects
     *
     * @param player
     */
    public void resetPlayer(Player player) {
        // getLogger().info("DEBUG: clear inventory = " +
        // Settings.clearInventory);
        if (Settings.clearInventory
                && (player.getWorld().getName().equalsIgnoreCase(Settings.worldName) || player.getWorld().getName()
                .equalsIgnoreCase(Settings.worldName + "_nether"))) {
            // Clear their inventory and equipment and set them as survival
            player.getInventory().clear(); // Javadocs are wrong - this does not
            // clear armor slots! So...
            player.getInventory().setArmorContents(null);
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
            player.getEquipment().clear();
        }
        if (!player.isOp()) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        if (Settings.resetChallenges) {
            // Reset the player's challenge status
            players.resetAllChallenges(player.getUniqueId(), false);
        }
        // Reset the island level
        players.setIslandLevel(player.getUniqueId(), 0);
        // Clear the starter island
        players.clearStartIslandRating(player.getUniqueId());
        // Save the player
        players.save(player.getUniqueId());
        TopTen.topTenAddEntry(player.getUniqueId(), 0);
        // Update the inventory
        player.updateInventory();
        if (Settings.resetEnderChest) {
            // Clear any Enderchest contents
            final ItemStack[] items = new ItemStack[player.getEnderChest().getContents().length];
            player.getEnderChest().setContents(items);
        }
        // Clear any potion effects
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public void restartEvents() {
        final PluginManager manager = getServer().getPluginManager();
        lavaListener = new LavaCheck(this);
        manager.registerEvents(lavaListener, this);
        // Enables warp signs in ASkyBlock
        warpSignsListener = new WarpSigns(this);
        manager.registerEvents(warpSignsListener, this);
    }

    public void unregisterEvents() {
        HandlerList.unregisterAll(warpSignsListener);
        HandlerList.unregisterAll(lavaListener);
    }

    /**
     * @return Locale for this player
     */
    public ASLocale myLocale(UUID player) {
        String locale = players.getLocale(player);
        if (locale.isEmpty() || !availableLocales.containsKey(locale)) {
            return availableLocales.get(Settings.defaultLanguage);
        }
        return availableLocales.get(locale);
    }

    /**
     * @return System locale
     */
    public ASLocale myLocale() {
        return availableLocales.get(Settings.defaultLanguage);
    }

    /**
     * @return the messages
     */
    public Messages getMessages() {
        return messages;
    }

    /**
     * @return the islandCmd
     */
    public IslandCmd getIslandCmd() {
        return islandCmd;
    }

    /**
     * @return the nameDB
     */
    public TinyDB getTinyDB() {
        return tinyDB;
    }

    public ChatListener getChatListener() {
        return chatListener;
    }

    /**
     * @return the warpPanel
     */
    public WarpPanel getWarpPanel() {
        if (warpPanel == null) {
            // Probably due to a reload
            warpPanel = new WarpPanel(this);
            getServer().getPluginManager().registerEvents(warpPanel, plugin);
        }
        return warpPanel;
    }

    /**
     * @return the schematicsPanel
     */
    public SchematicsPanel getSchematicsPanel() {
        return schematicsPanel;
    }

    /**
     * @return the settingsPanel
     */
    public SettingsPanel getSettingsPanel() {
        return settingsPanel;
    }

    /**
     * @return the availableLocales
     */
    public Map<String, ASLocale> getAvailableLocales() {
        return availableLocales;
    }

    /**
     * @param availableLocales the availableLocales to set
     */
    public void setAvailableLocales(HashMap<String, ASLocale> availableLocales) {
        this.availableLocales = availableLocales;
    }

    /**
     * @return the acidTask
     */
    public AcidTask getAcidTask() {
        return acidTask;
    }

    /**
     * @return the playerEvents
     */
    public PlayerEvents getPlayerEvents() {
        return playerEvents;
    }
}
