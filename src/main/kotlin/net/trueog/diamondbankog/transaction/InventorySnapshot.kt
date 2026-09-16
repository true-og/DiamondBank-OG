package net.trueog.diamondbankog.transaction

import java.util.*
import java.util.function.Consumer
import kotlinx.coroutines.launch
import net.trueog.diamondbankog.DiamondBankOG.Companion.scope
import net.trueog.diamondbankog.balance.BalanceManager
import net.trueog.diamondbankog.balance.shard.ShardType
import net.trueog.diamondbankog.transaction.InventoryLockExtensions.isInventoryLocked
import net.trueog.diamondbankog.util.ErrorHandler.handleError
import net.trueog.diamondbankog.util.InventoryExtensions.countTotal
import net.trueog.utilitiesog.UtilitiesOG
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class InventorySnapshot
private constructor(
    private val original: Inventory,
    private val heldItemSlot: Int,
    val holder: UUID,
    val balanceManager: BalanceManager,
) : Inventory by original {
    companion object {
        fun from(uuid: UUID, balanceManager: BalanceManager): InventorySnapshot {
            if (!Bukkit.isPrimaryThread()) {
                throw IllegalStateException("This method should only be called on the main thread")
            }
            if (!uuid.isInventoryLocked()) {
                throw IllegalStateException("Can only take a snapshot from a locked inventory")
            }
            val clonedInventory = Bukkit.createInventory(null, 36)
            val player = Bukkit.getPlayer(uuid)
            if (player == null) {
                clonedInventory.contents = UtilitiesOG.getInventoryData(uuid).map { it?.clone() }.toTypedArray()
                return InventorySnapshot(clonedInventory, UtilitiesOG.getHeldItemSlot(uuid), uuid, balanceManager)
            } else {
                clonedInventory.contents = player.inventory.contents.map { it?.clone() }.toTypedArray()
                return InventorySnapshot(
                    clonedInventory,
                    player.inventory.heldItemSlot,
                    player.inventory.holder!!.uniqueId,
                    balanceManager,
                )
            }
        }
    }

    fun restoreTo(uuid: UUID) {
        if (!Bukkit.isPrimaryThread()) {
            throw IllegalStateException("This method should only be called on the main thread")
        }
        if (!uuid.isInventoryLocked()) {
            throw IllegalStateException("Can only restore to a locked inventory")
        }
        val player = Bukkit.getPlayer(uuid)
        if (player == null) {
            UtilitiesOG.setInventoryData(uuid, this.contents.map { it?.clone() }.toTypedArray())
        } else {
            player.inventory.storageContents = this.contents.map { it?.clone() }.toTypedArray()
        }
        val inventoryShards = this.countTotal()
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
