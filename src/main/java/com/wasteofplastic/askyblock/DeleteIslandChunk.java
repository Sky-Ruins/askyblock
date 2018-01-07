/*******************************************************************************
 * This file is part of ASkyBlock.
 *
 *     ASkyBlock is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ASkyBlock is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ASkyBlock.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.wasteofplastic.askyblock;

import com.wasteofplastic.askyblock.nms.NMSAbstraction;
import com.wasteofplastic.askyblock.util.Pair;
import com.wasteofplastic.askyblock.util.Util;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

//import com.wasteofplastic.askyblock.nms.NMSAbstraction;

/**
 * Deletes islands fast using chunk regeneration
 *
 * @author tastybento
 */
public class DeleteIslandChunk {

    private Set<Pair> chunksToClear = new HashSet<>();
    private NMSAbstraction nms = null;

    public DeleteIslandChunk(final ASkyBlock plugin, final Island island) {
        final World world = island.getCenter().getWorld();
        if (world == null) {
            return;
        }
        // Determine if blocks need to be cleaned up or not
        boolean cleanUpBlocks = false;
        if (Settings.islandDistance - island.getProtectionSize() < 16) {
            cleanUpBlocks = true;
        }
        int range = island.getProtectionSize() / 2;
        int minProtectedX = island.getMinProtectedX(), minProtectedZ = island.getMinProtectedZ();
        final int minx = minProtectedX, minz = minProtectedZ;
        final int maxx = minProtectedX + island.getProtectionSize(), maxz = minProtectedZ + island.getProtectionSize();

        int blockX = island.getCenter().getBlockX(), blockZ = island.getCenter().getBlockZ();
        int islandSpacing = Settings.islandDistance - island.getProtectionSize();
        int minxX = (blockX - range - islandSpacing), minzZ = (blockZ - range - islandSpacing);
        int maxxX = (blockX + range + islandSpacing), maxzZ = (blockZ + range + islandSpacing);

        final Chunk minChunk = world.getBlockAt(minx, 0, minz).getChunk();
        final Chunk maxChunk = world.getBlockAt(maxx, 0, maxz).getChunk();

        for (int x = minChunk.getX(); x <= maxChunk.getX(); x++) {
            for (int z = minChunk.getZ(); z <= maxChunk.getZ(); z++) {
                boolean regen = true;

                Chunk chunk = world.getChunkAt(x, z);
                if (chunk.getBlock(0, 0, 0).getX() < minxX || chunk.getBlock(0, 0, 0).getZ() < minzZ
                        || chunk.getBlock(15, 0, 15).getX() > maxxX || chunk.getBlock(15, 0, 15).getZ() > maxzZ) {
                    regen = false;
                }

                if (regen) {
                    world.regenerateChunk(x, z);
                    World islandWorld = ASkyBlock.getIslandWorld();
                    if (Settings.newNether && Settings.createNether) {
                        if (world.equals(islandWorld)) {
                            ASkyBlock.getNetherWorld().regenerateChunk(x, z);
                        }
                        if (world.equals(ASkyBlock.getNetherWorld())) {
                            islandWorld.regenerateChunk(x, z);
                        }
                    }
                } else if (cleanUpBlocks) {
                    chunksToClear.add(new Pair(x, z));
                }
            }
        }
        plugin.getGrid().deleteIsland(island.getCenter());
        // Clear up any chunks
        if (!chunksToClear.isEmpty()) {
            try {
                nms = Util.checkVersion();
            } catch (ClassNotFoundException | InstantiationException | InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
                plugin.getLogger().warning("Cannot clean up blocks because there is no NMS acceleration available");
                return;
            }

            plugin.getLogger().info("Island delete: There are " + chunksToClear.size() + " chunks that need to be cleared up.");
            plugin.getLogger().info("Clean rate is " + Settings.cleanRate + " chunks per second. Should take ~"
                    + Math.round(chunksToClear.size() / Settings.cleanRate) + "s");

            new BukkitRunnable() {
                @Override
                public void run() {
                    Iterator<Pair> it = chunksToClear.iterator();
                    int count = 0;
                    while (it.hasNext() && count++ < Settings.cleanRate) {
                        Pair pair = it.next();
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                int xCoord = pair.getLeft() * 16 + x;
                                int zCoord = pair.getRight() * 16 + z;
                                if (island.inIslandSpace(xCoord, zCoord)) {
                                    for (int y = 0; y < ASkyBlock.getIslandWorld().getMaxHeight(); y++) {
                                        Block b = ASkyBlock.getIslandWorld().getBlockAt(xCoord, y, zCoord);
                                        Material bt = b.getType();
                                        Material setTo = Material.AIR;
                                        if (y < Settings.seaHeight) {
                                            setTo = Material.STATIONARY_WATER;
                                        }

                                        switch (bt) {
                                            case CHEST:
                                            case TRAPPED_CHEST:
                                            case FURNACE:
                                            case DISPENSER:
                                            case HOPPER:
                                                final InventoryHolder ih = ((InventoryHolder) b.getState());
                                                ih.getInventory().clear();
                                                b.setType(setTo);
                                                break;
                                            case AIR:
                                                if (setTo.equals(Material.STATIONARY_WATER)) {
                                                    nms.setBlockSuperFast(b, setTo.getId(), (byte) 0, false);
                                                }
                                            case STATIONARY_WATER:
                                                if (setTo.equals(Material.AIR)) {
                                                    nms.setBlockSuperFast(b, setTo.getId(), (byte) 0, false);
                                                }
                                            default:
                                                nms.setBlockSuperFast(b, setTo.getId(), (byte) 0, false);
                                                break;
                                        }

                                        if (Settings.newNether && Settings.createNether
                                                && y < ASkyBlock.getNetherWorld().getMaxHeight() - 8) {
                                            b = ASkyBlock.getNetherWorld().getBlockAt(xCoord, y, zCoord);
                                            bt = b.getType();
                                            if (bt != Material.AIR) {
                                                setTo = Material.AIR;
                                                switch (bt) {
                                                    case CHEST:
                                                    case TRAPPED_CHEST:
                                                    case FURNACE:
                                                    case DISPENSER:
                                                    case HOPPER:
                                                        final InventoryHolder ih = ((InventoryHolder) b.getState());
                                                        ih.getInventory().clear();
                                                        b.setType(setTo);
                                                        break;
                                                    default:
                                                        nms.setBlockSuperFast(b, setTo.getId(), (byte) 0, false);
                                                        break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        it.remove();
                    }
                    if (chunksToClear.isEmpty()) {
                        plugin.getLogger().info("Finished island deletion");
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 20L);

        }
    }

}