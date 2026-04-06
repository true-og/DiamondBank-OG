package net.trueog.diamondbankog.transaction

import java.util.*
import java.util.function.Consumer
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.balance.BalanceManager
import net.trueog.diamondbankog.balance.shard.ShardType
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.isLocked
import net.trueog.diamondbankog.util.ErrorHandler.handleError
import net.trueog.diamondbankog.util.InventoryExtensions.countTotal
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory

class InventorySnapshot
private constructor(
    private val original: Inventory,
    private val heldItemSlot: Int,
    val holder: UUID,
    val balanceManager: BalanceManager,
) : Inventory by original {

    companion object {
        fun from(inventory: PlayerInventory, balanceManager: BalanceManager): InventorySnapshot {
            if (!Bukkit.isPrimaryThread()) {
                throw IllegalStateException("This method should only be called on the main thread")
            }
            if (!inventory.isLocked()) {
                throw IllegalStateException("Can only take a snapshot from a locked inventory")
            }
            val clonedInventory = Bukkit.createInventory(null, 36)
            clonedInventory.contents = inventory.contents.map { it?.clone() }.toTypedArray()
            return InventorySnapshot(
                clonedInventory,
                inventory.heldItemSlot,
                inventory.holder!!.uniqueId,
                balanceManager,
            )
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
