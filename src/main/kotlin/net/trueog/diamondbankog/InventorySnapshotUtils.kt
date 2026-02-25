package net.trueog.diamondbankog

import kotlin.math.ceil
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

object InventorySnapshotUtils {
    /** @return the amount of shards that could be removed */
    suspend fun removeShards(inventory: InventorySnapshot, shards: Long): Result<Int> {
        require(shards > 0)

        val shards = shards.toInt()

        val notRemovedShards = inventory.removeItem(Shard.createItemStack(shards)).values.sumOf { it.amount }
        if (notRemovedShards == 0) return Result.success(shards)

        val diamondsToBeRemoved = ceil(notRemovedShards / 9.0).toInt()
        val diamondsRemainder = notRemovedShards % 9
        val shardsChange = if (diamondsRemainder == 0) 0 else 9 - diamondsRemainder
        val notRemovedDiamonds =
            inventory.removeItem(ItemStack(Material.DIAMOND, diamondsToBeRemoved)).values.sumOf { it.amount }
        if (notRemovedDiamonds == 0) {
            val addMap = inventory.addItem(Shard.createItemStack(shardsChange))
            if (addMap.isNotEmpty()) {
                balanceManager.addToBankShards(inventory.holder, addMap.values.sumOf { it.amount }.toLong()).getOrElse {
                    return Result.failure(it)
                }
            }
            return Result.success(shards)
        }

        val diamondBlocksToBeRemoved = ceil(notRemovedDiamonds / 9.0).toInt()
        val diamondBlocksRemainder = notRemovedDiamonds % 9
        val diamondsChange = if (diamondBlocksRemainder == 0) 0 else 9 - diamondBlocksRemainder
        val notRemovedDiamondBlocks =
            inventory.removeItem(ItemStack(Material.DIAMOND_BLOCK, diamondBlocksToBeRemoved)).values.sumOf { it.amount }
        if (notRemovedDiamondBlocks == 0) {
            val shardsAddMap = inventory.addItem(Shard.createItemStack(shardsChange))
            if (shardsAddMap.isNotEmpty()) {
                balanceManager
                    .addToBankShards(inventory.holder, shardsAddMap.values.sumOf { it.amount }.toLong())
                    .getOrElse {
                        return Result.failure(it)
                    }
            }
            val diamondsAddMap = inventory.addItem(ItemStack(Material.DIAMOND, diamondsChange))
            if (diamondsAddMap.isNotEmpty()) {
                balanceManager
                    .addToBankShards(inventory.holder, diamondsAddMap.values.sumOf { it.amount }.toLong() * 9)
                    .getOrElse {
                        return Result.failure(it)
                    }
            }
            return Result.success(shards)
        }
        return Result.success(shards - (notRemovedDiamondBlocks * 81 - diamondsChange * 9 - shardsChange))
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
