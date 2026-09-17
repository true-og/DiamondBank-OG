package net.trueog.diamondbankog.util

import net.trueog.diamondbankog.balance.shard.Shard
import net.trueog.diamondbankog.util.InventoryExtensions.countTotal
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.meta.BlockStateMeta

internal object PlayerInventoryExtensions {
    private fun PlayerInventory.allValues(material: Material): List<ItemStack> =
        this.all(material).values + listOfNotNull(this.itemInOffHand.takeIf { it.type == material })

    fun PlayerInventory.countTotal(): Long {
        return this.countShards() +
            this.countDiamonds() * 9 +
            this.countDiamondBlocks() * 81 +
            this.allValues(Material.SHULKER_BOX).sumOf {
                ((it.itemMeta as BlockStateMeta).blockState as ShulkerBox).inventory.countTotal()
            }
    }

    fun PlayerInventory.countShards(): Long {
        val inventoryShards =
            this.allValues(Material.PRISMARINE_SHARD).filter { Shard.isShardItem(it) }.sumOf { it.amount }
        return inventoryShards.toLong()
    }

    fun PlayerInventory.countDiamonds(): Long {
        val inventoryDiamonds = this.allValues(Material.DIAMOND).sumOf { it.amount }
        return inventoryDiamonds.toLong()
    }

    fun PlayerInventory.countDiamondBlocks(): Long {
        val inventoryDiamondBlocks = this.allValues(Material.DIAMOND_BLOCK).sumOf { it.amount }
        return inventoryDiamondBlocks.toLong()
    }
}
