package com.wasteofplastic.askyblock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Guardian;
import org.bukkit.entity.Monster;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class AcidTask {

    private ASkyBlock plugin;
    private Set<UUID> itemsInWater;
    private BukkitTask task;

    /**
     * Runs repeating tasks to deliver acid damage to mobs, etc.
     */
    public AcidTask(final ASkyBlock plugin) {
        this.plugin = plugin;
        itemsInWater = new HashSet<>();
        if (Settings.mobAcidDamage > 0D || Settings.animalAcidDamage > 0D) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                List<Entity> entList = ASkyBlock.getIslandWorld().getEntities();

                for (Entity current : entList) {
                    if (current instanceof Guardian) {
                        continue;
                    }

                    final Creature creature = current;

                    if (current instanceof Monster && Settings.mobAcidDamage > 0D) {
                        if ((current.getLocation().getBlock().getType() == Material.WATER)
                                || (current.getLocation().getBlock().getType() == Material.STATIONARY_WATER)) {
                            ((Monster) current).damage(Settings.mobAcidDamage);
                            // getLogger().info("Killing monster");
                        }
                    } else if (current instanceof Animals && Settings.animalAcidDamage > 0D) {
                        if (current.getLocation().getBlock().getType() == Material.WATER
                                || current.getLocation().getBlock().getType() == Material.STATIONARY_WATER) {
                            if (!current.getType().equals(EntityType.CHICKEN)) {
                                ((Animals) current).damage(Settings.animalAcidDamage);
                            } else if (Settings.damageChickens) {
                                ((Animals) current).damage(Settings.animalAcidDamage);
                            }
                            // getLogger().info("Killing animal");
                        }
                    }
                }
            }, 0L, 20L);
        }
        runAcidItemRemovalTask();
    }

    public void runAcidItemRemovalTask() {
        if (task != null) {
            task.cancel();
        }

        if (Settings.acidItemDestroyTime > 0) {
            task = new BukkitRunnable() {
                @Override
                public void run() {
                    List<Entity> entList = ASkyBlock.getIslandWorld().getEntities();
                    Set<UUID> newItemsInWater = new HashSet<>();

                    for (Entity current : entList) {
                        if (current.getType() != null && current.getType() == EntityType.DROPPED_ITEM
                                && (current.getLocation().getBlock().getType() == Material.WATER
                                || current.getLocation().getBlock().getType() == Material.STATIONARY_WATER)) {
                            if (itemsInWater.contains(current.getUniqueId())) {
                                current.getWorld().playSound(current.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 3F, 3F);
                                current.remove();
                            } else {
                                newItemsInWater.add(current.getUniqueId());
                            }
                        }
                    }
                    itemsInWater = newItemsInWater;
                }
            }.runTaskTimer(plugin, Settings.acidItemDestroyTime, Settings.acidItemDestroyTime);
        }
    }

}
