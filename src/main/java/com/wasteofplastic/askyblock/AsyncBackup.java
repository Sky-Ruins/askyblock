package com.wasteofplastic.askyblock;

import org.bukkit.Bukkit;

class AsyncBackup {

    /**
     * Class to save the register and name database. This is done in an async way.
     */
    AsyncBackup(final ASkyBlock plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            plugin.getGrid().saveGrid();
            plugin.getTinyDB().asyncSaveDB();
        }, Settings.backupDuration, Settings.backupDuration);
    }

}
