package com.wasteofplastic.askyblock;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class FileLister {

    private final static String FOLDER_PATH = "locale";
    private final ASkyBlock plugin;

    public FileLister(ASkyBlock plugin) {
        this.plugin = plugin;
    }

    public List<String> list() throws IOException {
        List<String> result = new ArrayList<>();


        File localeDir = new File(plugin.getDataFolder(), FOLDER_PATH);
        if (localeDir.exists()) {
            FilenameFilter ymlFilter = (dir, name) -> {
                String lowercaseName = name.toLowerCase();
                //plugin.getLogger().info("DEBUG: filename = " + name);
                if (lowercaseName.endsWith(".yml") && name.length() == 9 && name.substring(2, 3).equals("-")) {
                    return true;
                } else if (lowercaseName.endsWith(".yml") && !lowercaseName.equals("locale.yml")) {
                    plugin.getLogger().severe("Filename " + name + " is not in the correct format for a locale file - skipping...");
                }
                return false;
            };
            String[] files = localeDir.list(ymlFilter);
            if (files == null) {
                plugin.getLogger().severe("Failed to collect list of files...");
                return result;
            }

            for (String fileName : files) {
                result.add(fileName.replace(".yml", ""));
            }
            // Finish if there are any files in this folder
            if (!result.isEmpty()) {
                return result;
            }
        }
        File jarfile;

        try {
            Method method = JavaPlugin.class.getDeclaredMethod("getFile");
            method.setAccessible(true);
            jarfile = (File) method.invoke(plugin);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new IOException(e);
        }

        JarFile jar = new JarFile(jarfile);
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String path = entry.getName();
            if (!path.startsWith(FOLDER_PATH)) {
                continue;
            }

            if (entry.getName().endsWith(".yml")) {
                String name = entry.getName().replace(".yml", "").replace("locale/", "");
                if (name.length() == 5 && name.substring(2, 3).equals("-")) {
                    result.add(name);
                }
            }
        }
        jar.close();
        return result;
    }
}