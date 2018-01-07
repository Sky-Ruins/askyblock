package com.wasteofplastic.askyblock.util;

import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.Settings;
import com.wasteofplastic.askyblock.nms.NMSAbstraction;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A set of utility methods
 *
 * @author tastybento
 */
public class Util {

    private static ASkyBlock plugin = ASkyBlock.getPlugin();
    private static Long x = System.nanoTime();

    /**
     * Loads a YAML file and if it does not exist it is looked for in the JAR
     *
     * @param file
     * @return
     */
    public static YamlConfiguration loadYamlFile(String file) {
        File dataFolder = plugin.getDataFolder();
        File yamlFile = new File(dataFolder, file);

        YamlConfiguration config = null;
        if (yamlFile.exists()) {
            try {
                config = new YamlConfiguration();
                config.load(yamlFile);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Create the missing file
            config = new YamlConfiguration();
            if (!file.startsWith("players")) {
                plugin.getLogger().info("No " + file + " found. Creating it...");
            }
            try {
                if (plugin.getResource(file) != null) {
                    plugin.getLogger().info("Using default found in jar file.");
                    plugin.saveResource(file, false);
                    config = new YamlConfiguration();
                    config.load(yamlFile);
                } else {
                    config.save(yamlFile);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create the " + file + " file!");
            }
        }
        return config;
    }

    /**
     * Saves a YAML file
     *
     * @param yamlFile
     * @param fileLocation
     */
    public static void saveYamlFile(YamlConfiguration yamlFile, String fileLocation) {
        File dataFolder = plugin.getDataFolder();
        File file = new File(dataFolder, fileLocation);

        try {
            yamlFile.save(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Cuts up a string into multiple lines with the same color code at the
     * start of each line
     *
     * @param color
     * @param longLine
     * @param length
     * @return List containing the colored lines
     */
    public static List<String> chop(ChatColor color, String longLine, int length) {
        List<String> result = new ArrayList<String>();
        if (longLine.contains("|") || longLine.contains("§")) {
            // Split pip character requires escaping it
            String[] split = longLine.split("\\|");
            return new ArrayList<String>(Arrays.asList(split));
        }
        // int multiples = longLine.length() / length;
        int i = 0;
        for (i = 0; i < longLine.length(); i += length) {
            // for (int i = 0; i< (multiples*length); i += length) {
            int endIndex = Math.min(i + length, longLine.length());
            String line = longLine.substring(i, endIndex);
            // Do the following only if i+length is not the end of the string
            if (endIndex < longLine.length()) {
                // Check if last character in this string is not a space
                if (!line.substring(line.length() - 1).equals(" ")) {
                    // If it is not a space, check to see if the next character
                    // in long line is a space.
                    if (!longLine.substring(endIndex, endIndex + 1).equals(" ")) {
                        // If it is not, then we are cutting a word in two and
                        // need to backtrack to the last space if possible
                        int lastSpace = line.lastIndexOf(" ");
                        // Only do this if there is a space in the line to
                        // backtrack to...
                        if (lastSpace != -1 && lastSpace < line.length()) {
                            line = line.substring(0, lastSpace);
                            i -= (length - lastSpace - 1);
                        }
                    }
                }
            }
            // }
            result.add(color + line);
        }
        // result.add(color + longLine.substring(i, longLine.length()));
        return result;
    }

    /**
     * Converts block face direction to radial degrees. Returns 0 if block face
     * is not radial.
     *
     * @param face
     * @return degrees
     */
    public static float blockFaceToFloat(BlockFace face) {
        switch (face) {
            case EAST:
                return 90F;
            case EAST_NORTH_EAST:
                return 67.5F;
            case EAST_SOUTH_EAST:
                return 0F;
            case NORTH:
                return 0F;
            case NORTH_EAST:
                return 45F;
            case NORTH_NORTH_EAST:
                return 22.5F;
            case NORTH_NORTH_WEST:
                return 337.5F;
            case NORTH_WEST:
                return 315F;
            case SOUTH:
                return 180F;
            case SOUTH_EAST:
                return 135F;
            case SOUTH_SOUTH_EAST:
                return 157.5F;
            case SOUTH_SOUTH_WEST:
                return 202.5F;
            case SOUTH_WEST:
                return 225F;
            case WEST:
                return 270F;
            case WEST_NORTH_WEST:
                return 292.5F;
            case WEST_SOUTH_WEST:
                return 247.5F;
            default:
                return 0F;
        }
    }

    /**
     * Converts a name like IRON_INGOT into Iron Ingot to improve readability
     *
     * @param ugly The string such as IRON_INGOT
     * @return A nicer version, such as Iron Ingot
     *
     * Credits to mikenon on GitHub!
     */
    public static String prettifyText(String ugly) {
        if (!ugly.contains("_") && (!ugly.equals(ugly.toUpperCase()))) {
            return ugly;
        }
        String fin = "";
        ugly = ugly.toLowerCase();
        if (ugly.contains("_")) {
            String[] splt = ugly.split("_");
            int i = 0;
            for (String s : splt) {
                i += 1;
                fin += Character.toUpperCase(s.charAt(0)) + s.substring(1);
                if (i < splt.length) {
                    fin += " ";
                }
            }
        } else {
            fin += Character.toUpperCase(ugly.charAt(0)) + ugly.substring(1);
        }
        return fin;
    }

    /**
     * Converts a serialized location to a Location. Returns null if string is
     * empty
     *
     * @param s - serialized location in format "world:x:y:z"
     * @return Location
     */
    static public Location getLocationString(final String s) {
        if (s == null || s.trim() == "") {
            return null;
        }
        final String[] parts = s.split(":");
        if (parts.length == 4) {
            final World w = Bukkit.getServer().getWorld(parts[0]);
            if (w == null) {
                return null;
            }
            final int x = Integer.parseInt(parts[1]);
            final int y = Integer.parseInt(parts[2]);
            final int z = Integer.parseInt(parts[3]);
            return new Location(w, x, y, z);
        } else if (parts.length == 6) {
            final World w = Bukkit.getServer().getWorld(parts[0]);
            if (w == null) {
                return null;
            }
            final int x = Integer.parseInt(parts[1]);
            final int y = Integer.parseInt(parts[2]);
            final int z = Integer.parseInt(parts[3]);
            final float yaw = Float.intBitsToFloat(Integer.parseInt(parts[4]));
            final float pitch = Float.intBitsToFloat(Integer.parseInt(parts[5]));
            return new Location(w, x, y, z, yaw, pitch);
        }
        return null;
    }

    /**
     * Converts a location to a simple string representation
     * If location is null, returns empty string
     *
     * @param location
     * @return String of location
     */
    static public String getStringLocation(final Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ()
                + ":" + Float
                .floatToIntBits(location.getYaw()) + ":" + Float.floatToIntBits(location.getPitch());
    }

    /**
     * Returns all of the items that begin with the given start,
     * ignoring case.  Intended for tabcompletion.
     *
     * @param list
     * @param start
     * @return List of items that start with the letters
     */
    public static List<String> tabLimit(final List<String> list, final String start) {
        final List<String> returned = new ArrayList<String>();
        for (String s : list) {
            if (s == null) {
                continue;
            }
            if (s.toLowerCase().startsWith(start.toLowerCase())) {
                returned.add(s);
            }
        }

        return returned;
    }

    /**
     * Gets a list of all players who are currently online.
     *
     * @return list of online players
     */
    public static List<String> getOnlinePlayerList() {
        return getOnlinePlayerList(null);
    }

    /**
     * Return a list of online players this player can see, i.e. are not invisible
     *
     * @param player
     * @return
     */
    public static List<String> getOnlinePlayerList(Player player) {
        final List<String> returned = new ArrayList<String>();
        for (Player p : Bukkit.getServer().getOnlinePlayers()) {
            if (player == null) {
                returned.add(p.getName());
            } else if (player.canSee(p)) {
                returned.add(p.getName());
            }
        }
        return returned;
    }

    /**
     * Checks what version the server is running and picks the appropriate NMS handler, or fallback
     *
     * @return NMSAbstraction class
     * @throws ClassNotFoundException
     * @throws IllegalArgumentException
     * @throws SecurityException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     */
    public static NMSAbstraction checkVersion() throws ClassNotFoundException, IllegalArgumentException,
            SecurityException, InstantiationException, IllegalAccessException, InvocationTargetException,
            NoSuchMethodException {
        String serverPackageName = Bukkit.getServer().getClass().getPackage().getName();
        String pluginPackageName = ASkyBlock.getPlugin().getClass().getPackage().getName();
        String version = serverPackageName.substring(serverPackageName.lastIndexOf('.') + 1);
        Class<?> clazz;
        try {
            //plugin.getLogger().info("Trying " + pluginPackageName + ".nms." + version + ".NMSHandler");
            clazz = Class.forName(pluginPackageName + ".nms." + version + ".NMSHandler");
        } catch (Exception e) {
            Bukkit.getLogger().info("No NMS Handler found for " + version + ", falling back to Bukkit API.");
            clazz = Class.forName(pluginPackageName + ".nms.fallback.NMSHandler");
        }
        //plugin.getLogger().info("DEBUG: " + serverPackageName);
        //plugin.getLogger().info("DEBUG: " + pluginPackageName);
        // Check if we have a NMSAbstraction implementing class at that location.
        if (NMSAbstraction.class.isAssignableFrom(clazz)) {
            return (NMSAbstraction) clazz.getConstructor().newInstance();
        } else {
            throw new IllegalStateException("Class " + clazz.getName() + " does not implement NMSAbstraction");
        }
    }

    /**
     * @return random double using XORShift random number generator
     */
    public static double randomDouble() {
        return (double) randomLong() / Long.MAX_VALUE;
    }

    /**
     * @return random long number using XORShift random number generator
     */
    public static long randomLong() {
        x ^= (x << 21);
        x ^= (x >>> 35);
        x ^= (x << 4);
        return Math.abs(x);
    }

    /**
     * Changes the setting in config.yml to a new value without removing comments (saveConfig() removes comments)
     *
     * @param oldSetting
     * @param newSetting
     * @throws IOException
     */
    public static void setConfig(String setting, String oldSetting, String newSetting) throws IOException {
        setYamlConfig(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml", setting, oldSetting, newSetting);
    }

    /**
     * Changes the setting in a YAML file to a new value without removing comments (saveConfig() removes comments)
     *
     * @param absoluteFilename
     * @param setting
     * @param oldSetting
     * @param newSetting
     * @throws IOException
     */
    public static void setYamlConfig(String absoluteFilename, String setting, String oldSetting, String newSetting) throws
            IOException {
        Path path = Paths.get(absoluteFilename);
        Charset charset = StandardCharsets.UTF_8;
        String content = new String(Files.readAllBytes(path), charset);
        content = content.replaceAll(setting + ": " + oldSetting, setting + ": " + newSetting);
        Files.write(path, content.getBytes(charset));
    }

    /**
     * Changes a setting in all player files in the player folder. If the setting does not exist, no change is made
     * This is not a true YAML change, if the setting name exists multiple times in the file, all lines will be changed.
     * The setting must include any spaces at the front if required
     *
     * @param playerFolder
     * @param setting - name of the YAML setting, e.g., locale
     * @param newSettingValue - the new value for this setting
     * @throws IOException
     */
    public static void setPlayerYamlConfig(File playerFolder, String setting, String newSettingValue) throws IOException {
        FilenameFilter ymlFilter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                String lowercaseName = name.toLowerCase();
                if (lowercaseName.endsWith(".yml")) {
                    return true;
                } else {
                    return false;
                }
            }
        };
        for (File file : playerFolder.listFiles(ymlFilter)) {
            Path path = Paths.get(file.getAbsolutePath());
            Charset charset = StandardCharsets.UTF_8;

            List<String> fileContent = new ArrayList<>(Files.readAllLines(path, charset));

            for (int i = 0; i < fileContent.size(); i++) {
                if (fileContent.get(i).startsWith(setting)) {
                    fileContent.set(i, setting + ": " + newSettingValue);
                    break;
                }
            }

            Files.write(path, fileContent, charset);
        }
    }

    /**
     * Display message to player in action bar (1.11+ or chat)
     *
     * @param player
     * @param message
     */
    public static void sendEnterExit(Player player, String message) {
        if (!Settings.showInActionBar
                || Bukkit.getVersion().contains("(MC: 1.7")
                || Bukkit.getVersion().contains("(MC: 1.8")
                || Bukkit.getVersion().contains("(MC: 1.9")
                || Bukkit.getVersion().contains("(MC: 1.10")) {
            sendMessage(player, message);
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "minecraft:title " + player.getName() + " actionbar {\"text\":\"" + ChatColor.stripColor(message) + "\"}");
    }

    /**
     * Send a message to sender if message is not empty. Does not include color codes or spaces
     *
     * @param sender
     * @param message
     */
    public static void sendMessage(CommandSender sender, String message) {
        if (!ChatColor.stripColor(message).trim().isEmpty()) {
            sender.sendMessage(message);
        }
    }

    /**
     * Results a list of items in a player's hands. Works for older versions of servers
     *
     * @param player
     * @return list of itemstacks
     */
    @SuppressWarnings("deprecation")
    public static List<ItemStack> getPlayerInHandItems(Player player) {
        List<ItemStack> result = new ArrayList<ItemStack>(2);
        if (Bukkit.getVersion().contains("(MC: 1.7")
                || Bukkit.getVersion().contains("(MC: 1.8")) {
            if (player.getItemInHand() != null) {
                result.add(player.getItemInHand());
            }
            return result;
        }
        if (player.getInventory().getItemInMainHand() != null) {
            result.add(player.getInventory().getItemInMainHand());
        }
        if (player.getInventory().getItemInOffHand() != null) {
            result.add(player.getInventory().getItemInOffHand());
        }
        return result;
    }

    /**
     * Checks if player has this type of item in either hand
     *
     * @param player
     * @param type
     * @return true if they are holding an item of type type
     */
    public static boolean playerIsHolding(Player player, Material type) {
        if (Bukkit.getVersion().contains("(MC: 1.7")
                || Bukkit.getVersion().contains("(MC: 1.8")) {
            if (player.getItemInHand() != null && player.getItemInHand().getType().equals(type)) {
                return true;
            }
            return false;
        }
        if (player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInMainHand().getType().equals(type)) {
            return true;
        }
        if (player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInOffHand().getType().equals(type)) {
            return true;
        }
        return false;
    }

    public static void runCommand(final Player player, final String string) {
        Bukkit.getScheduler().runTask(plugin, new Runnable() {

            @Override
            public void run() {
                player.performCommand(string);
            }
        });

    }
}