package net.trueog.diamondbankog.util

import net.trueog.diamondbankog.balance.shard.Shard
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.meta.BlockStateMeta

internal object InventoryExtensions {
    fun Inventory.countTotal(): Long {
        return this.countShards() +
            this.countDiamonds() * 9 +
            this.countDiamondBlocks() * 81 +
            this.all(Material.SHULKER_BOX).values.sumOf {
                ((it.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.countTotal()
            }
    }

    fun Inventory.countShards(): Long {
        val inventoryShards =
            this.all(Material.PRISMARINE_SHARD).values.filter { Shard.isShardItem(it) }.sumOf { it.amount }
        return inventoryShards.toLong()
    }

    fun Inventory.countDiamonds(): Long {
        val inventoryDiamonds = this.all(Material.DIAMOND).values.sumOf { it.amount }
        return inventoryDiamonds.toLong()
    }

    fun Inventory.countDiamondBlocks(): Long {
        val inventoryDiamondBlocks = this.all(Material.DIAMOND_BLOCK).values.sumOf { it.amount }
        return inventoryDiamondBlocks.toLong()
    }
}
