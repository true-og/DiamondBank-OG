package net.trueog.diamondbankog

import java.util.*
import java.util.function.Consumer
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.balanceManager
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.ErrorHandler.handleError
import net.trueog.diamondbankog.InventoryExtensions.countTotal
import net.trueog.diamondbankog.InventoryExtensions.isLocked
import net.trueog.diamondbankog.PostgreSQL.ShardType
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

class InventorySnapshot
private constructor(private val original: Inventory, private val heldItemSlot: Int, val holder: UUID) :
    Inventory by original {
    companion object {
        fun from(inventory: PlayerInventory): InventorySnapshot {
            if (!Bukkit.isPrimaryThread()) {
                throw IllegalStateException("This method should only be called on the main thread")
            }
            if (!inventory.isLocked()) {
                throw IllegalStateException("Can only take a snapshot from a locked inventory")
            }
            val clonedInventory = Bukkit.createInventory(null, 36)
            clonedInventory.contents = inventory.contents.map { it?.clone() }.toTypedArray()
            return InventorySnapshot(clonedInventory, inventory.heldItemSlot, inventory.holder!!.uniqueId)
        }

        fun withContents(contents: Array<ItemStack?>): InventorySnapshot {
            val clonedInventory = Bukkit.createInventory(null, 36)
            clonedInventory.contents = contents.map { it?.clone() }.toTypedArray()
            return InventorySnapshot(clonedInventory, 0, UUID.fromString("00000000-0000-0000-0000-000000000000"))
        }
    }

    fun restoreTo(targetInventory: PlayerInventory) {
        if (!Bukkit.isPrimaryThread()) {
            throw IllegalStateException("This method should only be called on the main thread")
        }
        if (!targetInventory.isLocked()) {
            throw IllegalStateException("Can only restore to a locked inventory")
        }
        targetInventory.storageContents = this.contents.map { it?.clone() }.toTypedArray()

        val uuid = targetInventory.holder!!.uniqueId
        val inventoryShards = targetInventory.countTotal()
        scope.launch {
            balanceManager.setPlayerShards(uuid, inventoryShards, ShardType.INVENTORY).getOrElse { handleError(it) }
        }
    }

    var itemInMainHand: ItemStack
        get() {
            val item = original.getItem(heldItemSlot)
            return item ?: ItemStack(Material.AIR)
        }
        set(value) = original.setItem(heldItemSlot, value)

    override fun forEach(action: Consumer<in ItemStack>?) {
        original.forEach(action)
    }

    override fun spliterator(): Spliterator<ItemStack?> {
        return original.spliterator()
    }
}
