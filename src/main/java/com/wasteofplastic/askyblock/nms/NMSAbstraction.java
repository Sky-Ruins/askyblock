package com.wasteofplastic.askyblock.nms;

import com.wasteofplastic.org.jnbt.Tag;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

public interface NMSAbstraction {

    /**
     * Update the low-level chunk information for the given block to the new block ID and data.  This
     * change will not be propagated to clients until the chunk is refreshed to them.
     */
    void setBlockSuperFast(Block block, int blockId, byte data, boolean applyPhysics);

    ItemStack setBook(Tag item);

    /**
     * Sets a block to be an item stack
     */
    void setFlowerPotBlock(Block block, ItemStack itemStack);

    boolean isPotion(ItemStack item);

    /**
     * Returns a potion ItemStack
     */
    ItemStack setPotion(Material itemMaterial, Tag itemTag, ItemStack chestItem);

    /**
     * Gets a monster egg itemstack
     */
    ItemStack getSpawnEgg(EntityType type, int amount);
}
