package com.wasteofplastic.askyblock;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tiny database for a hashmap that is not used very often, but could be very big so I
 * don't want it in memory.
 *
 * @author tastybento
 */
public class TinyDB {

    private static File database;
    private ASkyBlock plugin;
    private ConcurrentHashMap<String, UUID> treeMap;
    private boolean dbReady, savingFlag;

    /**
     * Opens the database
     */
    TinyDB(ASkyBlock plugin) {
        this.plugin = plugin;
        this.treeMap = new ConcurrentHashMap<>();
        database = new File(plugin.getDataFolder(), "name-uuid.txt");
        if (!database.exists()) {
            convertFiles();
        } else {
            dbReady = true;
        }
    }

    private void convertFiles() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            FilenameFilter ymlFilter = (dir, name) -> name.toLowerCase().endsWith(".yml");
            int count = 0;

            File[] files = plugin.getPlayersFolder().listFiles(ymlFilter);
            if (files == null) {
                System.err.println("[ASkyBlock/AcidIsland]: Could not get players folder...");
                return;
            }

            for (final File file : files) {
                if (count++ % 1000 == 0) {
                    System.out.println("[ASkyBlock]: Processed " + count + " names to database");
                }

                try {
                    String uuid = file.getName().substring(0, file.getName().length() - 4);
                    final UUID playerUUID = UUID.fromString(uuid);

                    try (Scanner scanner = new Scanner(file)) {
                        while (scanner.hasNextLine()) {
                            final String lineFromFile = scanner.nextLine();
                            if (lineFromFile.contains("playerName:")) {
                                String playerName = lineFromFile.substring(lineFromFile.indexOf(' ')).trim();
                                treeMap.put(playerName.toLowerCase(), playerUUID);
                                break;
                            }
                        }
                    }
                } catch (IOException ex) {
                    System.err.println("[ASkyBlock/AcidIsland]: Problem reading " + file.getName() + " skipping...");
                }
            }
            saveDB();
            treeMap.clear();
            System.out.println("Complete. Processed " + count + " names to database");
            dbReady = true;
        });
    }

    /**
     * Saves the DB
     */
    public void saveDB() {
        savingFlag = true;
        try {
            File newDB = new File(plugin.getDataFolder(), "name-uuid-new.txt");
            try (PrintWriter out = new PrintWriter(newDB)) {
                for (Entry<String, UUID> entry : treeMap.entrySet()) {
                    out.println(entry.getKey());
                    out.println(entry.getValue().toString());
                }

                if (database.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(database))) {
                        String line = br.readLine(), uuid = br.readLine();
                        while (line != null) {
                            if (!treeMap.containsKey(line)) {
                                out.println(line);
                                out.println(uuid);
                            }

                            line = br.readLine();
                            uuid = br.readLine();
                        }
                    }
                }
            }

            try {
                Files.move(newDB.toPath(), database.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                plugin.getLogger().severe("Problem saving name database! Could not rename files!");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Problem saving name database!");
            e.printStackTrace();
        }
        savingFlag = false;
    }

    /**
     * Async Saving of the DB
     */
    public void asyncSaveDB() {
        if (!savingFlag) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveDB);
        }
    }

    /**
     * @return the dbReady
     */
    public boolean isDbReady() {
        return dbReady;
    }

    /**
     * Saves the player name to the database. Case insensitive!
     */
    public void savePlayerName(String playerName, UUID playerUUID) {
        if (playerName == null) {
            return;
        }
        treeMap.put(playerName.toLowerCase(), playerUUID);
    }

    /**
     * Gets the UUID for this player name or null if not known. Case insensitive!
     *
     * @return UUID of player, or null if unknown
     */
    public UUID getPlayerUUID(String playerName) {
        if (playerName == null) {
            return null;
        }

        return Optional.ofNullable(treeMap.get(playerName.toLowerCase())).orElseGet(() -> {
            try (BufferedReader br = new BufferedReader(new FileReader(database))) {
                String line = br.readLine(), uuid = br.readLine();

                while (line != null && !line.equalsIgnoreCase(playerName)) {
                    line = br.readLine();
                    uuid = br.readLine();
                }

                if (line == null) {
                    return null;
                }

                UUID result = UUID.fromString(uuid);
                treeMap.put(playerName.toLowerCase(), result);
                return result;
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Gets players name from tiny database
     *
     * @return Name or empty string if unknown
     */
    public String getPlayerName(UUID playerUuid) {
        if (playerUuid == null) {
            return "";
        }

        try (BufferedReader br = new BufferedReader(new FileReader(database))) {
            String line = br.readLine(), uuid = br.readLine();

            while (uuid != null && !uuid.equals(playerUuid.toString())) {
                line = br.readLine();
                uuid = br.readLine();
            }

            if (line == null) {
                return "";
            }

            treeMap.put(line.toLowerCase(), playerUuid);
            return line;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

}
