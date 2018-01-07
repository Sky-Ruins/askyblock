package com.wasteofplastic.askyblock.listeners;

import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.Island;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.WeakHashMap;

/**
 * This class manages flying mobs. If they exist the spawned island's limits they will be removed.
 *
 * @author tastybento
 */
public class FlyingMobEvents implements Listener {

    private final static boolean DEBUG = false;
    private final ASkyBlock plugin;
    private WeakHashMap<Entity, Island> mobSpawnInfo;

    /**
     * @param plugin
     */
    public FlyingMobEvents(ASkyBlock plugin) {
        this.plugin = plugin;
        this.mobSpawnInfo = new WeakHashMap<Entity, Island>();
        new BukkitRunnable() {

            public void run() {
                //Bukkit.getLogger().info("DEBUG: checking - mobspawn size = " + mobSpawnInfo.size());
                Iterator<Entry<Entity, Island>> it = mobSpawnInfo.entrySet().iterator();
                while (it.hasNext()) {
                    Entry<Entity, Island> entry = it.next();
                    if (entry.getKey() == null) {
                        //Bukkit.getLogger().info("DEBUG: removing null entity");
                        it.remove();
                    } else {
                        if (entry.getKey() instanceof LivingEntity) {
                            if (!entry.getValue().inIslandSpace(entry.getKey().getLocation())) {
                                //Bukkit.getLogger().info("DEBUG: removing entity outside of island");
                                it.remove();
                                // Kill mob
                                LivingEntity mob = (LivingEntity) entry.getKey();
                                mob.setHealth(0);
                                entry.getKey().remove();
                            } else {
                                //Bukkit.getLogger().info("DEBUG: entity " + entry.getKey().getName() + " is in island space");
                            }
                        } else {
                            // Not living entity
                            it.remove();
                        }
                    }
                }
            }

        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Track where the mob was created. This will determine its allowable movement zone.
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void mobSpawn(CreatureSpawnEvent e) {
        // Only cover withers in the island world
        if (!IslandGuard.inWorld(e.getEntity())) {
            return;
        }
        if (!e.getEntityType().equals(EntityType.WITHER) && !e.getEntityType().equals(EntityType.BLAZE) && !e.getEntityType()
                .equals(EntityType.GHAST)) {
            return;
        }
        if (DEBUG) {
            plugin.getLogger().info("Flying mobs " + e.getEventName());
        }
        // Store where this mob originated
        Island island = plugin.getGrid().getIslandAt(e.getLocation());
        if (island != null) {
            if (DEBUG) {
                plugin.getLogger().info("DEBUG: Mob spawned on known island - id = " + e.getEntity().getUniqueId());
            }
            mobSpawnInfo.put(e.getEntity(), island);
        } // Else do nothing - maybe an Op spawned it? If so, on their head be it!
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void MobExplosion(EntityExplodeEvent e) {
        if (DEBUG) {
            plugin.getLogger().info(e.getEventName());
        }
        // Only cover in the island world
        if (e.getEntity() == null || !IslandGuard.inWorld(e.getEntity())) {
            return;
        }
        if (mobSpawnInfo.containsKey(e.getEntity().getUniqueId())) {
            // We know about this mob
            if (DEBUG) {
                plugin.getLogger().info("DEBUG: We know about this mob");
            }
            if (!mobSpawnInfo.get(e.getEntity().getUniqueId()).inIslandSpace(e.getLocation())) {
                // Cancel the explosion and block damage
                if (DEBUG) {
                    plugin.getLogger().info("DEBUG: cancel flying mob explosion");
                }
                e.blockList().clear();
                e.setCancelled(true);
            }
        }
    }

    /**
     * Deal with pre-explosions
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void WitherExplode(ExplosionPrimeEvent e) {
        if (DEBUG) {
            plugin.getLogger().info(e.getEventName());
        }
        // Only cover withers in the island world
        if (!IslandGuard.inWorld(e.getEntity()) || e.getEntity() == null) {
            return;
        }
        // The wither or wither skulls can both blow up
        if (e.getEntityType() == EntityType.WITHER) {
            //plugin.getLogger().info("DEBUG: Wither");
            // Check the location
            if (mobSpawnInfo.containsKey(e.getEntity().getUniqueId())) {
                // We know about this wither
                if (DEBUG) {
                    plugin.getLogger().info("DEBUG: We know about this wither");
                }
                if (!mobSpawnInfo.get(e.getEntity()).inIslandSpace(e.getEntity().getLocation())) {
                    // Cancel the explosion
                    if (DEBUG) {
                        plugin.getLogger().info("DEBUG: cancelling wither pre-explosion");
                    }
                    e.setCancelled(true);
                }
            }
            // Testing only e.setCancelled(true);
        }
        if (e.getEntityType() == EntityType.WITHER_SKULL) {
            //plugin.getLogger().info("DEBUG: Wither skull");
            // Get shooter
            Projectile projectile = (Projectile) e.getEntity();
            if (projectile.getShooter() instanceof Wither) {
                //plugin.getLogger().info("DEBUG: shooter is wither");
                Wither wither = (Wither) projectile.getShooter();
                // Check the location
                if (mobSpawnInfo.containsKey(wither.getUniqueId())) {
                    // We know about this wither
                    if (DEBUG) {
                        plugin.getLogger().info("DEBUG: We know about this wither");
                    }
                    if (!mobSpawnInfo.get(wither.getUniqueId()).inIslandSpace(e.getEntity().getLocation())) {
                        // Cancel the explosion
                        if (DEBUG) {
                            plugin.getLogger().info("DEBUG: cancel wither skull explosion");
                        }
                        e.setCancelled(true);
                    }
                }
            }
        }
    }

    /**
     * Withers change blocks to air after they are hit (don't know why)
     * This prevents this when the wither has been spawned by a visitor
     *
     * @param e
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void WitherChangeBlocks(EntityChangeBlockEvent e) {
        if (DEBUG) {
            plugin.getLogger().info(e.getEventName());
        }
        // Only cover withers in the island world
        if (e.getEntityType() != EntityType.WITHER || !IslandGuard.inWorld(e.getEntity())) {
            return;
        }
        if (mobSpawnInfo.containsKey(e.getEntity())) {
            // We know about this wither
            if (DEBUG) {
                plugin.getLogger().info("DEBUG: We know about this wither");
            }
            if (!mobSpawnInfo.get(e.getEntity()).inIslandSpace(e.getEntity().getLocation())) {
                // Cancel the block changes
                if (DEBUG) {
                    plugin.getLogger().info("DEBUG: cancelled wither block change");
                }
                e.setCancelled(true);
            }
        }
    }

    /**
     * Clean up the hashmap. It's probably not needed, but just in case.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void MobDeath(EntityDeathEvent e) {
        mobSpawnInfo.remove(e.getEntity());
    }
}