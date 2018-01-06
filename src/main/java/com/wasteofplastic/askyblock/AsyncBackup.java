package com.wasteofplastic.askyblock;

class AsyncBackup {

    /**
     * Class to save the register and name database. This is done in an async way.
     */
    AsyncBackup(final ASkyBlock plugin) {
        // Save grid every 5 minutes
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            plugin.getGrid().saveGrid();
            plugin.getTinyDB().asyncSaveDB();
        }, Settings.backupDuration, Settings.backupDuration);
    }

}
