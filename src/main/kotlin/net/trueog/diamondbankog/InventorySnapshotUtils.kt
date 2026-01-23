package net.trueog.diamondbankog

import kotlin.math.ceil
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object InventorySnapshotUtils {
    /** @return the amount of shards that could be removed. -1 if the change could not be put back */
    fun removeShards(inventory: InventorySnapshot, shards: Long): Int {
        require(shards > 0)

        val shards = shards.toInt()

        val notRemovedShards = inventory.removeItem(Shard.createItemStack(shards)).values.sumOf { it.amount }
        if (notRemovedShards == 0) return shards

        val diamondsToBeRemoved = ceil(notRemovedShards / 9.0).toInt()
        val diamondsRemainder = notRemovedShards % 9
        val shardsChange = if (diamondsRemainder == 0) 0 else 9 - diamondsRemainder
        val notRemovedDiamonds =
            inventory.removeItem(ItemStack(Material.DIAMOND, diamondsToBeRemoved)).values.sumOf { it.amount }
        if (notRemovedDiamonds == 0) {
            if (inventory.addItem(Shard.createItemStack(shardsChange)).isNotEmpty()) return -1
            return shards
        }

        val diamondBlocksToBeRemoved = ceil(notRemovedDiamonds / 9.0).toInt()
        val diamondBlocksRemainder = notRemovedDiamonds % 9
        val diamondsChange = if (diamondBlocksRemainder == 0) 0 else 9 - diamondBlocksRemainder
        val notRemovedDiamondBlocks =
            inventory.removeItem(ItemStack(Material.DIAMOND_BLOCK, diamondBlocksToBeRemoved)).values.sumOf { it.amount }
        if (notRemovedDiamondBlocks == 0) {
            if (inventory.addItem(Shard.createItemStack(shardsChange)).isNotEmpty()) return -1
            if (inventory.addItem(ItemStack(Material.DIAMOND, diamondsChange)).isNotEmpty()) return -1
            return shards
        }
        return shards - (notRemovedDiamondBlocks * 81 - diamondsChange * 9 - shardsChange)
    }

    /** @return the amount of currency items that could be removed in shards */
    fun removeAll(inventory: InventorySnapshot): Int {
        val removedShards = removeAllShards(inventory)
        val removedDiamonds = removeAllDiamonds(inventory)
        val removedDiamondBlocks = removeAllDiamondBlocks(inventory)
        return removedShards + (removedDiamonds * 9) + (removedDiamondBlocks * 81)
    }

    /** @return the amount of shards that could be removed */
    fun removeAllShards(inventory: InventorySnapshot): Int {
        val shards = countShards(inventory)
        inventory.removeItem(Shard.createItemStack(shards))
        return shards
    }

    /** @return the amount of diamonds that could be removed */
    fun removeAllDiamonds(inventory: InventorySnapshot): Int {
        val diamonds = countDiamonds(inventory)
        inventory.remove(Material.DIAMOND)
        return diamonds
    }

    /** @return the amount of diamond blocks that could be removed */
    fun removeAllDiamondBlocks(inventory: InventorySnapshot): Int {
        val diamondBlocks = countDiamondBlocks(inventory)
        inventory.remove(Material.DIAMOND_BLOCK)
        return diamondBlocks
    }

    fun countShards(inventory: InventorySnapshot): Int {
        return inventory.all(Material.PRISMARINE_SHARD).values.filter { Shard.isShardItem(it) }.sumOf { it.amount }
    }

    fun countDiamonds(inventory: InventorySnapshot): Int {
        return inventory.all(Material.DIAMOND).values.sumOf { it.amount }
    }

    fun countDiamondBlocks(inventory: InventorySnapshot): Int {
        return inventory.all(Material.DIAMOND_BLOCK).values.sumOf { it.amount }
    }
}
