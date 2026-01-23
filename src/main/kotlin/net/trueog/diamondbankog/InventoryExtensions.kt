package net.trueog.diamondbankog

import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.meta.BlockStateMeta
import java.util.*

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

    val lockMap: MutableSet<PlayerInventory> = Collections.newSetFromMap(Collections.synchronizedMap(WeakHashMap()))

    /** @return true is the inventory was locked, else false */
    fun PlayerInventory.lock(): Boolean {
        val added = lockMap.add(this)
        if (!added) throw IllegalStateException("Inventory was already locked")
        return true
    }

    fun PlayerInventory.unlock(): Boolean {
        val removed = lockMap.remove(this)
        if (!removed) throw IllegalStateException("Inventory was not locked")
        return true
    }

    fun PlayerInventory.isLocked(): Boolean {
        return this in lockMap
    }
}
